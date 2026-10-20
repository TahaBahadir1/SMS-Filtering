package com.smsfiltering.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SenderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v1/senders/lookup - kayitli gonderen buyuk/kucuk harf duyarsiz bulunmali")
    void shouldFindSenderIgnoreCase() throws Exception {
        String requestJson = """
                {
                    "sender": "banka-x"
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
    @DisplayName("POST /api/v1/senders/lookup - bos gonderen gonderildiginde 400 ve error mesaji donmeli")
    void shouldReturn400WhenSenderIsBlank() throws Exception {
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
    void shouldReturn400WhenSenderExceeds32Chars() throws Exception {
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
}
