package com.smsfiltering.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SenderSignalRequest(
        @NotBlank(message = "Gönderen bilgisi boş olamaz")
        @Size(max = 32, message = "Gönderen bilgisi en fazla 32 karakter olabilir")
        String sender,

        @NotBlank(message = "Sinyal kaynağı boş olamaz")
        String source,

        @NotNull(message = "Risk skoru boş olamaz")
        @DecimalMin(value = "0.00", message = "Risk skoru en az 0.00 olabilir")
        @DecimalMax(value = "1.00", message = "Risk skoru en fazla 1.00 olabilir")
        BigDecimal score
) {
}
