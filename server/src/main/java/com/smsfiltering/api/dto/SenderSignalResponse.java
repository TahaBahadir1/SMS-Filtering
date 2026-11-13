package com.smsfiltering.api.dto;

import java.math.BigDecimal;

public record SenderSignalResponse(
        String sender,
        BigDecimal riskScore,
        String riskLevel,
        boolean isFinalized
) {
}
