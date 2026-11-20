package com.smsfiltering.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gizlilik odaklı bellek içi hız sınırlayıcı (Rate Limiter) ve sinyal yazma koruma filtresi.
 *
 * - İstemci IP adresi asla düz metin olarak saklanmaz veya loglanmaz;
 *   JVM başlangıcında rastgele üretilen salt ile SHA-256 özetine dönüştürülür.
 * - Sinyal zehirlenmesini (signal poisoning) önlemek için /senders/signals uç noktası
 *   daha sıkı hız limitine tabidir ve yapılandırıldığında X-API-Key doğrulaması yapar.
 */
@Component
public class PrivacyPreservingRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiMetricsCollector metricsCollector;
    private final boolean enabled;
    private final int lookupLimitPerMinute;
    private final int signalLimitPerMinute;
    private final int defaultLimitPerMinute;
    private final String signalApiKey;
    private final byte[] ephemeralSalt;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public PrivacyPreservingRateLimitFilter(
            ApiMetricsCollector metricsCollector,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.lookup-per-minute:60}") int lookupLimitPerMinute,
            @Value("${app.rate-limit.signal-per-minute:20}") int signalLimitPerMinute,
            @Value("${app.rate-limit.default-per-minute:120}") int defaultLimitPerMinute,
            @Value("${app.security.signal-api-key:}") String signalApiKey
    ) {
        this.metricsCollector = metricsCollector;
        this.enabled = enabled;
        this.lookupLimitPerMinute = lookupLimitPerMinute;
        this.signalLimitPerMinute = signalLimitPerMinute;
        this.defaultLimitPerMinute = defaultLimitPerMinute;
        this.signalApiKey = signalApiKey == null ? "" : signalApiKey.trim();
        this.ephemeralSalt = new byte[16];
        new SecureRandom().nextBytes(this.ephemeralSalt);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/") || path.endsWith("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. Sinyal yazma uç noktası için isteğe bağlı API anahtarı kontrolü
        if (isSignalEndpoint(path) && !signalApiKey.isEmpty()) {
            String providedKey = request.getHeader(API_KEY_HEADER);
            if (providedKey == null || !MessageDigest.isEqual(
                    signalApiKey.getBytes(StandardCharsets.UTF_8),
                    providedKey.trim().getBytes(StandardCharsets.UTF_8)
            )) {
                metricsCollector.recordUnauthorizedSignal();
                writeJsonError(response, HttpStatus.UNAUTHORIZED, "Geçersiz veya eksik API anahtarı");
                return;
            }
        }

        // 2. Gizlilik odaklı hız sınırlama (Rate Limiting)
        if (enabled) {
            String bucketName = resolveBucket(path);
            int limit = resolveLimit(bucketName);
            String anonymousClientHash = hashClientKey(extractClientIdentifier(request), bucketName);

            long now = System.currentTimeMillis();
            WindowCounter counter = counters.compute(anonymousClientHash, (k, existing) -> {
                if (existing == null || now - existing.windowStartMs >= WINDOW_MILLIS) {
                    return new WindowCounter(now, new AtomicInteger(1));
                }
                existing.count.incrementAndGet();
                return existing;
            });

            // Bellek büyümesini önlemek için periyodik temizlik
            if (counters.size() > 10_000) {
                counters.entrySet().removeIf(e -> now - e.getValue().windowStartMs >= WINDOW_MILLIS);
            }

            int currentCount = counter.count.get();
            int remaining = Math.max(0, limit - currentCount);
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

            if (currentCount > limit) {
                metricsCollector.recordRateLimited();
                long retryAfterSeconds = Math.max(1L, (WINDOW_MILLIS - (now - counter.windowStartMs)) / 1000L);
                response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                writeJsonError(
                        response,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Çok fazla istek gönderildi, lütfen daha sonra tekrar deneyin"
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    public void resetCounters() {
        counters.clear();
    }

    private boolean isSignalEndpoint(String path) {
        return path.endsWith("/senders/signals");
    }

    private String resolveBucket(String path) {
        if (isSignalEndpoint(path)) {
            return "signals";
        }
        if (path.endsWith("/senders/lookup")) {
            return "lookup";
        }
        return "default";
    }

    private int resolveLimit(String bucket) {
        return switch (bucket) {
            case "signals" -> signalLimitPerMinute;
            case "lookup" -> lookupLimitPerMinute;
            default -> defaultLimitPerMinute;
        };
    }

    private String extractClientIdentifier(HttpServletRequest request) {
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private String hashClientKey(String clientIdentifier, String bucket) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ephemeralSalt);
            digest.update(bucket.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(clientIdentifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algoritması bulunamadı", e);
        }
    }

    private void writeJsonError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private record WindowCounter(long windowStartMs, AtomicInteger count) {
    }
}
