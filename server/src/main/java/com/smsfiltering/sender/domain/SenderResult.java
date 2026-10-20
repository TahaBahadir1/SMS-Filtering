package com.smsfiltering.sender.domain;

import java.math.BigDecimal;

public record SenderResult(
        boolean found,
        BigDecimal riskScore,
        String riskLevel,
        Boolean isFinalized
) {
    public static SenderResult notFound() {
        return new SenderResult(false, null, null, false);
    }
}
