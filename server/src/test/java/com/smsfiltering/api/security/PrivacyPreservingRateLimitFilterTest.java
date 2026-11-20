package com.smsfiltering.api.security;

import com.smsfiltering.api.GlobalExceptionHandler;
import com.smsfiltering.api.HealthController;
import com.smsfiltering.api.SenderController;
import com.smsfiltering.api.SenderControllerV2;
import com.smsfiltering.sender.application.port.AddSignalUseCase;
import com.smsfiltering.sender.application.port.LookupSenderUseCase;
import com.smsfiltering.sender.domain.RiskLevel;
import com.smsfiltering.sender.domain.Sender;
import com.smsfiltering.sender.domain.SenderResult;
import com.smsfiltering.sender.domain.SenderSignal;
import com.smsfiltering.sender.domain.SignalSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PrivacyPreservingRateLimitFilterTest {

    @Mock
    private LookupSenderUseCase lookupSenderUseCase;

    @Mock
    private AddSignalUseCase addSignalUseCase;

    @Test
    @DisplayName("Hiz limiti asildiginda HTTP 429 ve standart hata formati donmeli")
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        ApiMetricsCollector metrics = new ApiMetricsCollector();
        PrivacyPreservingRateLimitFilter filter = new PrivacyPreservingRateLimitFilter(
                metrics, true, 3, 2, 10, ""
        );
        SenderController controller = new SenderController(lookupSenderUseCase, addSignalUseCase, metrics);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter(filter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(lookupSenderUseCase.lookup("BANKA-X"))
                .thenReturn(new SenderResult(true, new BigDecimal("0.05"), "risksiz", true));

        String body = "{\"sender\":\"BANKA-X\"}";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/senders/lookup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "3"));
        }

        // 4. istek 429 donmeli
        mockMvc.perform(post("/api/v1/senders/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").exists());

        org.junit.jupiter.api.Assertions.assertEquals(1L, metrics.getTotalRateLimited());
        org.junit.jupiter.api.Assertions.assertEquals(3L, metrics.getTotalLookups());
    }

    @Test
    @DisplayName("Sinyal yazma ucnoktasi API anahtari yapilandirildiginda 401/200 dogrulamasi yapmali")
    void shouldEnforceApiKeyOnSignalEndpointWhenConfigured() throws Exception {
        ApiMetricsCollector metrics = new ApiMetricsCollector();
        PrivacyPreservingRateLimitFilter filter = new PrivacyPreservingRateLimitFilter(
                metrics, true, 60, 10, 120, "gizli-sinyal-anahtari"
        );
        SenderControllerV2 controllerV2 = new SenderControllerV2(lookupSenderUseCase, addSignalUseCase, metrics);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controllerV2)
                .addFilter(filter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Sender saved = new Sender(
                1L, "DOLANDIRICI-V2", new BigDecimal("0.95"), RiskLevel.RISKLI, true,
                OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(new SenderSignal(SignalSource.MANUEL, new BigDecimal("0.95"), OffsetDateTime.now()))
        );
        when(addSignalUseCase.addSignal(eq("DOLANDIRICI-V2"), eq("manuel"), any(BigDecimal.class)))
                .thenReturn(saved);

        String body = "{\"sender\":\"DOLANDIRICI-V2\",\"source\":\"manuel\",\"score\":0.95}";

        // Anahtarsiz -> 401
        mockMvc.perform(post("/api/v2/senders/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());

        // Yanlis anahtar -> 401
        mockMvc.perform(post("/api/v2/senders/signals")
                        .header("X-API-Key", "yanlis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        // Dogru anahtar -> 200 OK (v2 signals esitligi)
        mockMvc.perform(post("/api/v2/senders/signals")
                        .header("X-API-Key", "gizli-sinyal-anahtari")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender").value("DOLANDIRICI-V2"))
                .andExpect(jsonPath("$.riskLevel").value("riskli"))
                .andExpect(jsonPath("$.isFinalized").value(true));

        org.junit.jupiter.api.Assertions.assertEquals(2L, metrics.getTotalUnauthorizedSignals());
        org.junit.jupiter.api.Assertions.assertEquals(1L, metrics.getTotalSignals());
    }

    @Test
    @DisplayName("GET /api/v1/health ve /api/v2/health rate limit disinda kalarak anonim metrikleri donmeli")
    void shouldExposeHealthAndAnonymousMetricsWithoutRateLimit() throws Exception {
        ApiMetricsCollector metrics = new ApiMetricsCollector();
        metrics.recordLookup();
        metrics.recordSignal();

        PrivacyPreservingRateLimitFilter filter = new PrivacyPreservingRateLimitFilter(
                metrics, true, 1, 1, 1, ""
        );
        HealthController healthController = new HealthController(metrics);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(healthController)
                .addFilter(filter)
                .build();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.totalLookups").value(1))
                    .andExpect(jsonPath("$.totalSignals").value(1));
        }

        mockMvc.perform(get("/api/v2/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
