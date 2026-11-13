package com.smsfiltering.api;

import com.smsfiltering.sender.application.port.AddSignalUseCase;
import com.smsfiltering.sender.application.port.LookupSenderUseCase;
import com.smsfiltering.sender.domain.RiskLevel;
import com.smsfiltering.sender.domain.Sender;
import com.smsfiltering.sender.domain.SenderResult;
import com.smsfiltering.sender.domain.SenderSignal;
import com.smsfiltering.sender.domain.SignalSource;
import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SenderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LookupSenderUseCase lookupSenderUseCase;

    @Mock
    private AddSignalUseCase addSignalUseCase;

    @BeforeEach
    void setUp() {
        SenderController controller = new SenderController(lookupSenderUseCase, addSignalUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==========================================
    // 1. POST /api/v1/senders/lookup Testleri
    // ==========================================

    @Test
    @DisplayName("POST /api/v1/senders/lookup - kayitli gonderen bulundugunda 200 OK ve detaylar donmeli")
    void shouldFindSenderSuccessfully() throws Exception {
        when(lookupSenderUseCase.lookup("BANKA-X"))
                .thenReturn(new SenderResult(true, new BigDecimal("0.05"), "risksiz", true));

        String requestJson = """
                {
                    "sender": "BANKA-X"
                }
                """;

        mockMvc.perform(post("/api/v1/senders/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.riskScore").value(0.05))
                .andExpect(jsonPath("$.riskLevel").value("risksiz"))
                .andExpect(jsonPath("$.isFinalized").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/senders/lookup - bulunamayan gonderen icin 200 ve found=false donmeli")
    void shouldReturnNotFoundStatusWhenSenderDoesNotExist() throws Exception {
        when(lookupSenderUseCase.lookup("BILINMEYEN_GONDEREN"))
                .thenReturn(new SenderResult(false, null, null, false));

        String requestJson = """
                {
                    "sender": "BILINMEYEN_GONDEREN"
                }
                """;

        mockMvc.perform(post("/api/v1/senders/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.riskScore", nullValue()))
                .andExpect(jsonPath("$.riskLevel", nullValue()))
                .andExpect(jsonPath("$.isFinalized").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/senders/lookup - bos gonderen gonderildiginde 400 donmeli")
    void shouldReturn400WhenLookupSenderIsBlank() throws Exception {
        String requestJson = """
                {
                    "sender": ""
                }
                """;

        mockMvc.perform(post("/api/v1/senders/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/v1/senders/lookup - 32 karakterden uzun gonderen gonderildiginde 400 donmeli")
    void shouldReturn400WhenLookupSenderExceeds32Chars() throws Exception {
        String requestJson = """
                {
                    "sender": "BU_GONDEREN_ADI_KESINLIKLE_32_KARAKTERDEN_DAHA_UZUNDUR"
                }
                """;

        mockMvc.perform(post("/api/v1/senders/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ==========================================
    // 2. POST /api/v1/senders/signals Testleri (İP 2)
    // ==========================================

    @Test
    @DisplayName("POST /api/v1/senders/signals - manuel kaynakli sinyal hemen kesinlesmeli (isFinalized=true)")
    void shouldFinalizeImmediatelyWhenManualSignalAdded() throws Exception {
        Sender manualSender = new Sender(
                1L,
                "BET-VIP",
                new BigDecimal("0.95"),
                RiskLevel.RISKLI,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of(new SenderSignal(SignalSource.MANUEL, new BigDecimal("0.95"), OffsetDateTime.now()))
        );

        when(addSignalUseCase.addSignal(eq("BET-VIP"), eq("manuel"), eq(new BigDecimal("0.95"))))
                .thenReturn(manualSender);

        String requestJson = """
                {
                    "sender": "BET-VIP",
                    "source": "manuel",
                    "score": 0.95
                }
                """;

        mockMvc.perform(post("/api/v1/senders/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender").value("BET-VIP"))
                .andExpect(jsonPath("$.riskScore").value(0.95))
                .andExpect(jsonPath("$.riskLevel").value("riskli"))
                .andExpect(jsonPath("$.isFinalized").value(true));

        verify(addSignalUseCase).addSignal("BET-VIP", "manuel", new BigDecimal("0.95"));
    }

    @Test
    @DisplayName("POST /api/v1/senders/signals - tek buyuk_veri kaynagi kesinlesmemeli (isFinalized=false)")
    void shouldNotFinalizeWithSingleNonManualSignal() throws Exception {
        Sender singleSignalSender = new Sender(
                2L,
                "YENI-KAMPANYA",
                new BigDecimal("0.50"),
                RiskLevel.ORTA_RISKLI,
                false,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of(new SenderSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.50"), OffsetDateTime.now()))
        );

        when(addSignalUseCase.addSignal(eq("YENI-KAMPANYA"), eq("buyuk_veri"), eq(new BigDecimal("0.50"))))
                .thenReturn(singleSignalSender);

        String requestJson = """
                {
                    "sender": "YENI-KAMPANYA",
                    "source": "buyuk_veri",
                    "score": 0.50
                }
                """;

        mockMvc.perform(post("/api/v1/senders/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender").value("YENI-KAMPANYA"))
                .andExpect(jsonPath("$.riskScore").value(0.50))
                .andExpect(jsonPath("$.riskLevel").value("orta_riskli"))
                .andExpect(jsonPath("$.isFinalized").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/senders/signals - iki farkli kaynak ayni risk duzeyindeyse kesinlesmeli (isFinalized=true)")
    void shouldFinalizeWhenTwoDifferentSourcesHaveSameRiskLevel() throws Exception {
        // buyuk_veri (0.80) + yapay_zeka (0.90) -> ortalama 0.85 -> ikisi de RISKLI -> isFinalized = true
        Sender finalizedSender = new Sender(
                3L,
                "KAZAN-VIP",
                new BigDecimal("0.85"),
                RiskLevel.RISKLI,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of(
                        new SenderSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.80"), OffsetDateTime.now()),
                        new SenderSignal(SignalSource.YAPAY_ZEKA, new BigDecimal("0.90"), OffsetDateTime.now())
                )
        );

        when(addSignalUseCase.addSignal(eq("KAZAN-VIP"), eq("yapay_zeka"), eq(new BigDecimal("0.90"))))
                .thenReturn(finalizedSender);

        String requestJson = """
                {
                    "sender": "KAZAN-VIP",
                    "source": "yapay_zeka",
                    "score": 0.90
                }
                """;

        mockMvc.perform(post("/api/v1/senders/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender").value("KAZAN-VIP"))
                .andExpect(jsonPath("$.riskScore").value(0.85))
                .andExpect(jsonPath("$.riskLevel").value("riskli"))
                .andExpect(jsonPath("$.isFinalized").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/senders/signals - bos gonderen degeri 400 donmeli")
    void shouldReturn400WhenSignalSenderIsBlank() throws Exception {
        String requestJson = """
                {
                    "sender": "",
                    "source": "buyuk_veri",
                    "score": 0.50
                }
                """;

        mockMvc.perform(post("/api/v1/senders/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(addSignalUseCase);
    }

    @Test
    @DisplayName("POST /api/v1/senders/signals - 32 karakteri asan gonderen degeri 400 donmeli")
    void shouldReturn400WhenSignalSenderExceeds32Chars() throws Exception {
        String requestJson = """
                {
                    "sender": "BU_GONDEREN_ADI_KESINLIKLE_32_KARAKTERDEN_DAHA_UZUNDUR",
                    "source": "buyuk_veri",
                    "score": 0.50
                }
                """;

        mockMvc.perform(post("/api/v1/senders/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(addSignalUseCase);
    }

    @Test
    @DisplayName("POST /api/v1/senders/signals - bos sinyal kaynagi 400 donmeli")
    void shouldReturn400WhenSignalSourceIsBlank() throws Exception {
        String requestJson = """
                {
                    "sender": "TEST-GONDEREN",
                    "source": "",
                    "score": 0.50
                }
                """;

        mockMvc.perform(post("/api/v1/senders/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(addSignalUseCase);
    }

    @Test
    @DisplayName("POST /api/v1/senders/signals - gecersiz sinyal kaynagi 400 donmeli")
    void shouldReturn400WhenSignalSourceIsInvalid() throws Exception {
        when(addSignalUseCase.addSignal(eq("TEST-GONDEREN"), eq("bilinmeyen_kaynak"), any()))
                .thenThrow(new IllegalArgumentException("Unknown signal source: bilinmeyen_kaynak"));

        String requestJson = """
                {
                    "sender": "TEST-GONDEREN",
                    "source": "bilinmeyen_kaynak",
                    "score": 0.50
                }
                """;

        mockMvc.perform(post("/api/v1/senders/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown signal source: bilinmeyen_kaynak"));
    }

    @Test
    @DisplayName("POST /api/v1/senders/signals - 1.00 uzerinde veya negatif risk skoru 400 donmeli")
    void shouldReturn400WhenSignalScoreOutOfRange() throws Exception {
        String invalidScoreAbove1 = """
                {
                    "sender": "TEST-GONDEREN",
                    "source": "buyuk_veri",
                    "score": 1.50
                }
                """;

        mockMvc.perform(post("/api/v1/senders/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidScoreAbove1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        String invalidScoreNegative = """
                {
                    "sender": "TEST-GONDEREN",
                    "source": "buyuk_veri",
                    "score": -0.10
                }
                """;

        mockMvc.perform(post("/api/v1/senders/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidScoreNegative))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(addSignalUseCase);
    }
}
