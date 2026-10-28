package com.smsfiltering.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KeywordControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/keywords - version parametresi verilmediginde (default=0) tam kategori ve konu listesi donmeli")
    void shouldReturnFullCategoryAndTopicListWhenVersionIsZero() throws Exception {
        mockMvc.perform(get("/api/v1/keywords")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.upToDate").value(false))
                .andExpect(jsonPath("$.categories", hasSize(5)))
                .andExpect(jsonPath("$.topics", hasSize(5)))
                .andExpect(jsonPath("$.topics[0].id").exists())
                .andExpect(jsonPath("$.topics[0].name").exists())
                .andExpect(jsonPath("$.topics[0].keywords").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/keywords?version=2 - version guncel oldugunda upToDate=true, bos kategori ve bos konu listesi donmeli")
    void shouldReturnEmptyListWhenVersionIsUpToDate() throws Exception {
        mockMvc.perform(get("/api/v1/keywords")
                        .param("version", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.upToDate").value(true))
                .andExpect(jsonPath("$.categories", hasSize(0)))
                .andExpect(jsonPath("$.topics", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/keywords?version=1 - eski surum gonderildiginde upToDate=false ve guncel liste donmeli")
    void shouldReturnFullListWhenOldVersionProvided() throws Exception {
        mockMvc.perform(get("/api/v1/keywords")
                        .param("version", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.upToDate").value(false))
                .andExpect(jsonPath("$.categories", hasSize(5)))
                .andExpect(jsonPath("$.topics", hasSize(5)));
    }
}
