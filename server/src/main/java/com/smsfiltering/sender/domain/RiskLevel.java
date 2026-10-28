package com.smsfiltering.sender.domain;

import java.math.BigDecimal;

public enum RiskLevel {
    RISKSIZ("risksiz"),
    ORTA_RISKLI("orta_riskli"),
    RISKLI("riskli");

    private static final BigDecimal THRESHOLD_LOW = new BigDecimal("0.30");
    private static final BigDecimal THRESHOLD_HIGH = new BigDecimal("0.70");

    private final String value;

    RiskLevel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RiskLevel fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (RiskLevel level : values()) {
            if (level.value.equalsIgnoreCase(value.trim())) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown risk level: " + value);
    }

    public static RiskLevel determine(BigDecimal score) {
        if (score == null) {
            throw new IllegalArgumentException("Score cannot be null");
        }
        if (score.compareTo(THRESHOLD_LOW) < 0) {
            return RISKSIZ;
        } else if (score.compareTo(THRESHOLD_HIGH) < 0) {
            return ORTA_RISKLI;
        } else {
            return RISKLI;
        }
    }
}
