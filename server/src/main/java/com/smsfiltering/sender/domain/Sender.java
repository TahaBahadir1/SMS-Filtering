package com.smsfiltering.sender.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Sender {
    private Long id;
    private final String senderValue;
    private BigDecimal riskScore;
    private RiskLevel riskLevel;
    private boolean isFinalized;
    private final OffsetDateTime firstSeen;
    private OffsetDateTime lastUpdated;
    private final List<SenderSignal> signals;

    public Sender(Long id, String senderValue, BigDecimal riskScore, RiskLevel riskLevel,
                  boolean isFinalized, OffsetDateTime firstSeen, OffsetDateTime lastUpdated,
                  List<SenderSignal> signals) {
        if (senderValue == null || senderValue.isBlank()) {
            throw new IllegalArgumentException("Sender value cannot be blank");
        }
        this.id = id;
        this.senderValue = senderValue;
        this.riskScore = riskScore != null ? riskScore.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.riskLevel = riskLevel != null ? riskLevel : RiskLevel.RISKSIZ;
        this.isFinalized = isFinalized;
        this.firstSeen = firstSeen != null ? firstSeen : OffsetDateTime.now();
        this.lastUpdated = lastUpdated != null ? lastUpdated : OffsetDateTime.now();
        this.signals = signals != null ? new ArrayList<>(signals) : new ArrayList<>();
    }

    public static Sender createNew(String senderValue) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Sender(null, senderValue, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                RiskLevel.RISKSIZ, false, now, now, new ArrayList<>());
    }

    public void addSignal(SignalSource source, BigDecimal score, OffsetDateTime timestamp) {
        if (source == null) {
            throw new IllegalArgumentException("Signal source cannot be null");
        }
        if (score == null) {
            throw new IllegalArgumentException("Signal score cannot be null");
        }
        OffsetDateTime signalTime = timestamp != null ? timestamp : OffsetDateTime.now();
        this.signals.add(new SenderSignal(source, score, signalTime));
        recalculateRisk();
        this.lastUpdated = signalTime;
    }

    public void recalculateRisk() {
        if (signals.isEmpty()) {
            this.riskScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            this.riskLevel = RiskLevel.RISKSIZ;
            this.isFinalized = false;
            return;
        }

        // Her kaynaktan EN SON sinyali bul
        Map<SignalSource, SenderSignal> latestSignalsBySource = new HashMap<>();
        for (SenderSignal s : signals) {
            SenderSignal current = latestSignalsBySource.get(s.getSource());
            if (current == null) {
                latestSignalsBySource.put(s.getSource(), s);
            } else {
                if (s.getCreatedAt().isAfter(current.getCreatedAt())) {
                    latestSignalsBySource.put(s.getSource(), s);
                } else if (s.getCreatedAt().isEqual(current.getCreatedAt())) {
                    if (s.getId() != null && current.getId() != null) {
                        if (s.getId() > current.getId()) {
                            latestSignalsBySource.put(s.getSource(), s);
                        }
                    } else {
                        latestSignalsBySource.put(s.getSource(), s);
                    }
                }
            }
        }

        // risk_score = her kaynaktan EN SON sinyalin skorlarının ortalaması
        BigDecimal sum = BigDecimal.ZERO;
        for (SenderSignal s : latestSignalsBySource.values()) {
            sum = sum.add(s.getScore());
        }
        BigDecimal count = BigDecimal.valueOf(latestSignalsBySource.size());
        this.riskScore = sum.divide(count, 2, RoundingMode.HALF_UP);

        // risk_level: skor < 0.30 risksiz; 0.30 ile 0.70 arası (0.70 hariç) orta_riskli; >= 0.70 riskli
        this.riskLevel = RiskLevel.determine(this.riskScore);

        // is_finalized: 'manuel' kaynaklı sinyal varsa true;
        // yoksa en az 2 farklı kaynaktan sinyal varsa ve bu kaynakların risk düzeyleri aynıysa true;
        // aksi halde false
        boolean hasManuel = latestSignalsBySource.containsKey(SignalSource.MANUEL);
        if (hasManuel) {
            this.isFinalized = true;
        } else if (latestSignalsBySource.size() >= 2) {
            Set<RiskLevel> sourceLevels = latestSignalsBySource.values().stream()
                    .map(s -> RiskLevel.determine(s.getScore()))
                    .collect(Collectors.toSet());
            this.isFinalized = (sourceLevels.size() == 1);
        } else {
            this.isFinalized = false;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSenderValue() {
        return senderValue;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public boolean isFinalized() {
        return isFinalized;
    }

    public OffsetDateTime getFirstSeen() {
        return firstSeen;
    }

    public OffsetDateTime getLastUpdated() {
        return lastUpdated;
    }

    public List<SenderSignal> getSignals() {
        return Collections.unmodifiableList(signals);
    }
}
