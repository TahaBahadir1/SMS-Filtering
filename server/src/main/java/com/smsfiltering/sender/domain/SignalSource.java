package com.smsfiltering.sender.domain;

public enum SignalSource {
    BUYUK_VERI("buyuk_veri"),
    YAPAY_ZEKA("yapay_zeka"),
    MANUEL("manuel");

    private final String value;

    SignalSource(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SignalSource fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Signal source cannot be null");
        }
        for (SignalSource source : values()) {
            if (source.value.equalsIgnoreCase(value.trim())) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown signal source: " + value);
    }
}
