package com.smsfiltering.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SenderLookupRequest(
        @NotBlank(message = "Gönderen bilgisi boş olamaz")
        @Size(max = 32, message = "Gönderen bilgisi en fazla 32 karakter olabilir")
        String sender
) {
}
