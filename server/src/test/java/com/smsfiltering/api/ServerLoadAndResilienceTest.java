package com.smsfiltering.api;

import com.smsfiltering.api.security.ApiMetricsCollector;
import com.smsfiltering.api.security.PrivacyPreservingRateLimitFilter;
import com.smsfiltering.sender.application.port.AddSignalUseCase;
import com.smsfiltering.sender.application.port.LookupSenderUseCase;
import com.smsfiltering.sender.domain.SenderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * İP 5: Sunucu Eşzamanlı Yük, Gecikme (p50/p95/p99) ve Rate-Limit Dayanıklılık Testi.
 */
@ExtendWith(MockitoExtension.class)
class ServerLoadAndResilienceTest {

    @Mock
    private LookupSenderUseCase lookupSenderUseCase;

    @Mock
    private AddSignalUseCase addSignalUseCase;

    @Test
    @DisplayName("Eszamanli 60 istek altinda yanit sureleri (p50/p95/p99) ve 429 korumasi dogrulanmali")
    void shouldMeasureConcurrentLatencyAndEnforceRateLimitCleanly() throws Exception {
        ApiMetricsCollector metrics = new ApiMetricsCollector();
        int lookupLimit = 40;
        int totalRequests = 60;

        PrivacyPreservingRateLimitFilter filter = new PrivacyPreservingRateLimitFilter(
                metrics, true, lookupLimit, 20, 120, ""
        );
        SenderController controller = new SenderController(lookupSenderUseCase, addSignalUseCase, metrics);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter(filter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(lookupSenderUseCase.lookup(anyString()))
                .thenReturn(new SenderResult(true, new BigDecimal("0.05"), "risksiz", true));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Long> latenciesNs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger okCount = new AtomicInteger(0);
        AtomicInteger rateLimitedCount = new AtomicInteger(0);
        AtomicInteger error5xxCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            final int idx = i;
            tasks.add(() -> {
                long start = System.nanoTime();
                MvcResult res = mockMvc.perform(post("/api/v1/senders/lookup")
                                .header("X-Forwarded-For", "198.51.100.10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sender\":\"SENDER-" + idx + "\"}"))
                        .andReturn();
                long elapsedNs = System.nanoTime() - start;
                latenciesNs.add(elapsedNs);

                int status = res.getResponse().getStatus();
                if (status == 200) {
                    okCount.incrementAndGet();
                } else if (status == 429) {
                    rateLimitedCount.incrementAndGet();
                } else if (status >= 500) {
                    error5xxCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        Collections.sort(latenciesNs);
        double p50Ms = latenciesNs.get((int) (latenciesNs.size() * 0.50)) / 1_000_000.0;
        double p95Ms = latenciesNs.get((int) (latenciesNs.size() * 0.95)) / 1_000_000.0;
        double p99Ms = latenciesNs.get((int) (latenciesNs.size() * 0.99)) / 1_000_000.0;

        System.out.printf("LOAD TEST -> Total: %d | 200 OK: %d | 429 RateLimited: %d | 5xx: %d | p50: %.2f ms | p95: %.2f ms | p99: %.2f ms%n",
                totalRequests, okCount.get(), rateLimitedCount.get(), error5xxCount.get(), p50Ms, p95Ms, p99Ms);

        assertEquals(0, error5xxCount.get(), "Yuk altinda hicbir 5xx sunucu hatasi olusmamali");
        assertEquals(lookupLimit, okCount.get(), "Tam olarak limit kadar istek 200 OK almali");
        assertEquals(totalRequests - lookupLimit, rateLimitedCount.get(), "Limit ustu istekler 429 ile sinirlanmali");
        assertTrue(p95Ms < 250.0, "p95 yanit suresi makul sinirda olmali");
    }
}
