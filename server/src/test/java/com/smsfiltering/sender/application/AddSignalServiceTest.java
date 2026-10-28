package com.smsfiltering.sender.application;

import com.smsfiltering.sender.application.port.SenderRepositoryPort;
import com.smsfiltering.sender.domain.RiskLevel;
import com.smsfiltering.sender.domain.Sender;
import com.smsfiltering.sender.domain.SignalSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddSignalServiceTest {

    @Mock
    private SenderRepositoryPort senderRepositoryPort;

    private AddSignalService addSignalService;

    @BeforeEach
    void setUp() {
        addSignalService = new AddSignalService(senderRepositoryPort);
    }

    @Test
    @DisplayName("Gönderen mevcut değilse yeni oluşturulup sinyal eklenmeli ve kaydedilmeli")
    void shouldCreateNewSenderWhenNotExists() {
        when(senderRepositoryPort.findDomainBySenderValueIgnoreCase("YENI-GONDEREN"))
                .thenReturn(Optional.empty());
        when(senderRepositoryPort.save(any(Sender.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Sender result = addSignalService.addSignal("YENI-GONDEREN", "manuel", new BigDecimal("0.85"));

        assertNotNull(result);
        assertEquals("YENI-GONDEREN", result.getSenderValue());
        assertEquals(new BigDecimal("0.85"), result.getRiskScore());
        assertEquals(RiskLevel.RISKLI, result.getRiskLevel());
        assertTrue(result.isFinalized());
        assertEquals(1, result.getSignals().size());
        assertEquals(SignalSource.MANUEL, result.getSignals().get(0).getSource());

        verify(senderRepositoryPort).save(any(Sender.class));
    }

    @Test
    @DisplayName("Gönderen mevcutsa mevcut gönderene sinyal eklenip risk ve son güncelleme güncellenmeli")
    void shouldUpdateExistingSenderWhenAlreadyExists() {
        Sender existing = Sender.createNew("BANKA-X");
        OffsetDateTime initialTime = OffsetDateTime.now().minusHours(1);
        existing.addSignal(SignalSource.BUYUK_VERI, new BigDecimal("0.10"), initialTime);

        when(senderRepositoryPort.findDomainBySenderValueIgnoreCase("BANKA-X"))
                .thenReturn(Optional.of(existing));
        when(senderRepositoryPort.save(any(Sender.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Sender result = addSignalService.addSignal("BANKA-X", "yapay_zeka", new BigDecimal("0.20"));

        assertNotNull(result);
        assertEquals(2, result.getSignals().size());
        // (0.10 + 0.20) / 2 = 0.15
        assertEquals(new BigDecimal("0.15"), result.getRiskScore());
        assertEquals(RiskLevel.RISKSIZ, result.getRiskLevel());
        // İki kaynak aynı düzeyde (risksiz) -> kesinleşti
        assertTrue(result.isFinalized());
        assertTrue(result.getLastUpdated().isAfter(initialTime));

        ArgumentCaptor<Sender> captor = ArgumentCaptor.forClass(Sender.class);
        verify(senderRepositoryPort).save(captor.capture());
        assertEquals(new BigDecimal("0.15"), captor.getValue().getRiskScore());
    }

    @Test
    @DisplayName("Geçersiz gönderen değeri verildiğinde hata fırlatmalı")
    void shouldThrowExceptionWhenSenderValueIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                addSignalService.addSignal("", "buyuk_veri", new BigDecimal("0.50")));
        assertThrows(IllegalArgumentException.class, () ->
                addSignalService.addSignal(null, "buyuk_veri", new BigDecimal("0.50")));
    }

    @Test
    @DisplayName("Geçersiz sinyal kaynağı verildiğinde hata fırlatmalı")
    void shouldThrowExceptionWhenSourceIsInvalid() {
        assertThrows(IllegalArgumentException.class, () ->
                addSignalService.addSignal("TEST", "gecersiz_kaynak", new BigDecimal("0.50")));
    }
}
