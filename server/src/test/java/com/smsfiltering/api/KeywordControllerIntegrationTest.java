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
    @DisplayName("GET /api/v1/keywords - version parametresi verilmediginde (default=0) tam liste donmeli")
    void shouldReturnFullCategoryListWhenVersionIsZero() throws Exception {
        mockMvc.perform(get("/api/v1/keywords")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.upToDate").value(false))
                .andExpect(jsonPath("$.categories", hasSize(5)));
    }

    @Test
    @DisplayName("GET /api/v1/keywords?version=1 - version guncel oldugunda upToDate=true ve bos liste donmeli")
    void shouldReturnEmptyListWhenVersionIsUpToDate() throws Exception {
        mockMvc.perform(get("/api/v1/keywords")
                        .param("version", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.upToDate").value(true))
                .andExpect(jsonPath("$.categories", hasSize(0)));
    }
}
