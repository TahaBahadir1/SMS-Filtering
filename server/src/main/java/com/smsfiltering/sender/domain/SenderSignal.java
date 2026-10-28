package com.smsfiltering.sender.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

public class SenderSignal {
    private final Long id;
    private final SignalSource source;
    private final BigDecimal score;
    private final OffsetDateTime createdAt;

    public SenderSignal(Long id, SignalSource source, BigDecimal score, OffsetDateTime createdAt) {
        if (source == null) {
            throw new IllegalArgumentException("Signal source cannot be null");
        }
        if (score == null) {
            throw new IllegalArgumentException("Signal score cannot be null");
        }
        this.id = id;
        this.source = source;
        this.score = score.setScale(2, RoundingMode.HALF_UP);
        this.createdAt = createdAt != null ? createdAt : OffsetDateTime.now();
    }

    public SenderSignal(SignalSource source, BigDecimal score, OffsetDateTime createdAt) {
        this(null, source, score, createdAt);
    }

    public Long getId() {
        return id;
    }

    public SignalSource getSource() {
        return source;
    }

    public BigDecimal getScore() {
        return score;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
