package com.smsfiltering.api.dto;

import java.math.BigDecimal;

public record SenderLookupResponse(
        boolean found,
        BigDecimal riskScore,
        String riskLevel,
        Boolean isFinalized
) {
}
