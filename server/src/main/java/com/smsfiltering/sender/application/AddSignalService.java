package com.smsfiltering.sender.application;

import com.smsfiltering.sender.application.port.AddSignalUseCase;
import com.smsfiltering.sender.application.port.SenderRepositoryPort;
import com.smsfiltering.sender.domain.Sender;
import com.smsfiltering.sender.domain.SignalSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@Transactional
public class AddSignalService implements AddSignalUseCase {

    private final SenderRepositoryPort senderRepositoryPort;

    public AddSignalService(SenderRepositoryPort senderRepositoryPort) {
        this.senderRepositoryPort = senderRepositoryPort;
    }

    @Override
    public Sender addSignal(String senderValue, String source, BigDecimal score) {
        if (senderValue == null || senderValue.isBlank()) {
            throw new IllegalArgumentException("Sender value cannot be blank");
        }
        if (score == null) {
            throw new IllegalArgumentException("Score cannot be null");
        }

        SignalSource signalSource = SignalSource.fromValue(source);
        String trimmedSender = senderValue.trim();

        Sender sender = senderRepositoryPort.findDomainBySenderValueIgnoreCase(trimmedSender)
                .orElseGet(() -> Sender.createNew(trimmedSender));

        sender.addSignal(signalSource, score, OffsetDateTime.now());

        return senderRepositoryPort.save(sender);
    }
}
