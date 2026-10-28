package com.smsfiltering.sender.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SenderRiskRulesTest {

    @Nested
    @DisplayName("1. Skor Ortalaması Testleri")
    class ScoreAverageTests {

        @Test
        @DisplayName("Her kaynaktan bir sinyal varken ortalama doğru hesaplanmalı")
        void shouldCalculateAverageOfLatestSignalsFromEachSource() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.20"), OffsetDateTime.now());
            sender.addSignal(SignalSource.YAPAY_ZEKA, new BigDecimal("0.40"), OffsetDateTime.now());

            assertEquals(new BigDecimal("0.30"), sender.getRiskScore());
            assertEquals(RiskLevel.ORTA_RISKLI, sender.getRiskLevel());
        }

        @Test
        @DisplayName("Bir kaynaktan birden fazla sinyal geldiğinde sadece EN SON sinyalin skoru dikkate alınmalı")
        void shouldUseOnlyLatestSignalPerSourceWhenCalculatingAverage() {
            Sender sender = Sender.createNew("TEST-SENDER");
            OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(10);
            OffsetDateTime t2 = OffsetDateTime.now().minusMinutes(5);
            OffsetDateTime t3 = OffsetDateTime.now();

            // BUYUK_VERI eski sinyal (0.10)
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.10"), t1);
            // BUYUK_VERI yeni sinyal (0.80) -> bu dikkate alınmalı
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.80"), t2);
            // YAPAY_ZEKA sinyal (0.60)
            sender.addSignal(SignalSource.YAPAY_ZEKA, new BigDecimal("0.60"), t3);

            // Beklenen: (0.80 + 0.60) / 2 = 0.70
            assertEquals(new BigDecimal("0.70"), sender.getRiskScore());
            assertEquals(RiskLevel.RISKLI, sender.getRiskLevel());
        }

        @Test
        @DisplayName("Üç farklı kaynaktan gelen en son sinyallerin ortalaması doğru hesaplanmalı")
        void shouldCalculateAverageAcrossThreeSources() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.30"), OffsetDateTime.now());
            sender.addSignal(SignalSource.YAPAY_ZEKA, new BigDecimal("0.60"), OffsetDateTime.now());
            sender.addSignal(SignalSource.MANUEL, new BigDecimal("0.90"), OffsetDateTime.now());

            // Beklenen: (0.30 + 0.60 + 0.90) / 3 = 0.60
            assertEquals(new BigDecimal("0.60"), sender.getRiskScore());
            assertEquals(RiskLevel.ORTA_RISKLI, sender.getRiskLevel());
        }
    }

    @Nested
    @DisplayName("2. Eşik Sınırları Testleri (0.29, 0.30, 0.69, 0.70)")
    class ThresholdBoundaryTests {

        @Test
        @DisplayName("Skor 0.29 -> risksiz olmalı (< 0.30)")
        void shouldBeRisksizWhenScoreIs029() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.29"), OffsetDateTime.now());

            assertEquals(new BigDecimal("0.29"), sender.getRiskScore());
            assertEquals(RiskLevel.RISKSIZ, sender.getRiskLevel());
            assertEquals("risksiz", sender.getRiskLevel().getValue());
        }

        @Test
        @DisplayName("Skor 0.30 -> orta_riskli olmalı (>= 0.30 ve < 0.70)")
        void shouldBeOrtaRiskliWhenScoreIs030() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.30"), OffsetDateTime.now());

            assertEquals(new BigDecimal("0.30"), sender.getRiskScore());
            assertEquals(RiskLevel.ORTA_RISKLI, sender.getRiskLevel());
            assertEquals("orta_riskli", sender.getRiskLevel().getValue());
        }

        @Test
        @DisplayName("Skor 0.69 -> orta_riskli olmalı (0.70 hariç)")
        void shouldBeOrtaRiskliWhenScoreIs069() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.69"), OffsetDateTime.now());

            assertEquals(new BigDecimal("0.69"), sender.getRiskScore());
            assertEquals(RiskLevel.ORTA_RISKLI, sender.getRiskLevel());
            assertEquals("orta_riskli", sender.getRiskLevel().getValue());
        }

        @Test
        @DisplayName("Skor 0.70 -> riskli olmalı (>= 0.70)")
        void shouldBeRiskliWhenScoreIs070() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.70"), OffsetDateTime.now());

            assertEquals(new BigDecimal("0.70"), sender.getRiskScore());
            assertEquals(RiskLevel.RISKLI, sender.getRiskLevel());
            assertEquals("riskli", sender.getRiskLevel().getValue());
        }

        @Test
        @DisplayName("RiskLevel.determine eşik sınırları doğrudan doğrulanmalı")
        void shouldDirectlyDetermineRiskLevels() {
            assertEquals(RiskLevel.RISKSIZ, RiskLevel.determine(new BigDecimal("0.00")));
            assertEquals(RiskLevel.RISKSIZ, RiskLevel.determine(new BigDecimal("0.29")));
            assertEquals(RiskLevel.ORTA_RISKLI, RiskLevel.determine(new BigDecimal("0.30")));
            assertEquals(RiskLevel.ORTA_RISKLI, RiskLevel.determine(new BigDecimal("0.69")));
            assertEquals(RiskLevel.RISKLI, RiskLevel.determine(new BigDecimal("0.70")));
            assertEquals(RiskLevel.RISKLI, RiskLevel.determine(new BigDecimal("1.00")));
        }
    }

    @Nested
    @DisplayName("3. Kesinleşme Kuralları Testleri (is_finalized)")
    class FinalizationRulesTests {

        @Test
        @DisplayName("Manuel kaynaklı tek sinyal varsa isFinalized true olmalı")
        void shouldBeFinalizedWhenSingleManuelSignalExists() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.MANUEL, new BigDecimal("0.85"), OffsetDateTime.now());

            assertTrue(sender.isFinalized());
        }

        @Test
        @DisplayName("Manuel sinyal varsa diğer kaynaklarla düzeyleri farklı olsa bile isFinalized true olmalı")
        void shouldBeFinalizedWhenManuelAndOtherSourcesExist() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.MANUEL, new BigDecimal("0.10"), OffsetDateTime.now());
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.90"), OffsetDateTime.now());

            assertTrue(sender.isFinalized());
        }

        @Test
        @DisplayName("Tek kaynak (manuel hariç) sinyal varsa isFinalized false olmalı")
        void shouldNotBeFinalizedWhenOnlyOneNonManuelSourceExists() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.85"), OffsetDateTime.now());

            assertFalse(sender.isFinalized());
        }

        @Test
        @DisplayName("Aynı kaynaktan birden fazla sinyal gelse bile tek kaynak olduğu için isFinalized false olmalı")
        void shouldNotBeFinalizedWhenMultipleSignalsComeFromSingleNonManuelSource() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.80"), OffsetDateTime.now().minusMinutes(5));
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.85"), OffsetDateTime.now());

            assertFalse(sender.isFinalized());
        }

        @Test
        @DisplayName("İki farklı kaynak aynı düzeyde olduğunda (ikisi de risksiz) isFinalized true olmalı")
        void shouldBeFinalizedWhenTwoSourcesHaveSameLevelRisksiz() {
            Sender sender = Sender.createNew("TEST-SENDER");
            // Her ikisi de risksiz (< 0.30)
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.10"), OffsetDateTime.now());
            sender.addSignal(SignalSource.YAPAY_ZEKA, new BigDecimal("0.25"), OffsetDateTime.now());

            assertTrue(sender.isFinalized());
            assertEquals(RiskLevel.RISKSIZ, sender.getRiskLevel());
        }

        @Test
        @DisplayName("İki farklı kaynak aynı düzeyde olduğunda (ikisi de orta_riskli) isFinalized true olmalı")
        void shouldBeFinalizedWhenTwoSourcesHaveSameLevelOrtaRiskli() {
            Sender sender = Sender.createNew("TEST-SENDER");
            // Her ikisi de orta_riskli (0.30 - 0.70)
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.35"), OffsetDateTime.now());
            sender.addSignal(SignalSource.YAPAY_ZEKA, new BigDecimal("0.65"), OffsetDateTime.now());

            assertTrue(sender.isFinalized());
            assertEquals(RiskLevel.ORTA_RISKLI, sender.getRiskLevel());
        }

        @Test
        @DisplayName("İki farklı kaynak aynı düzeyde olduğunda (ikisi de riskli) isFinalized true olmalı")
        void shouldBeFinalizedWhenTwoSourcesHaveSameLevelRiskli() {
            Sender sender = Sender.createNew("TEST-SENDER");
            // Her ikisi de riskli (>= 0.70)
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.75"), OffsetDateTime.now());
            sender.addSignal(SignalSource.YAPAY_ZEKA, new BigDecimal("0.90"), OffsetDateTime.now());

            assertTrue(sender.isFinalized());
            assertEquals(RiskLevel.RISKLI, sender.getRiskLevel());
        }

        @Test
        @DisplayName("İki farklı kaynak farklı düzeylerde olduğunda (risksiz vs orta_riskli) isFinalized false olmalı")
        void shouldNotBeFinalizedWhenTwoSourcesHaveDifferentLevelsRisksizAndOrtaRiskli() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.20"), OffsetDateTime.now()); // risksiz
            sender.addSignal(SignalSource.YAPAY_ZEKA, new BigDecimal("0.50"), OffsetDateTime.now()); // orta_riskli

            assertFalse(sender.isFinalized());
        }

        @Test
        @DisplayName("İki farklı kaynak farklı düzeylerde olduğunda (risksiz vs riskli) isFinalized false olmalı")
        void shouldNotBeFinalizedWhenTwoSourcesHaveDifferentLevelsRisksizAndRiskli() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.15"), OffsetDateTime.now()); // risksiz
            sender.addSignal(SignalSource.YAPAY_ZEKA, new BigDecimal("0.85"), OffsetDateTime.now()); // riskli

            assertFalse(sender.isFinalized());
        }

        @Test
        @DisplayName("İki farklı kaynak farklı düzeylerde olduğunda (orta_riskli vs riskli) isFinalized false olmalı")
        void shouldNotBeFinalizedWhenTwoSourcesHaveDifferentLevelsOrtaRiskliAndRiskli() {
            Sender sender = Sender.createNew("TEST-SENDER");
            sender.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.55"), OffsetDateTime.now()); // orta_riskli
            sender.addSignal(SignalSource.YAPAY_ZEKA, new BigDecimal("0.80"), OffsetDateTime.now()); // riskli

            assertFalse(sender.isFinalized());
        }
    }
}
