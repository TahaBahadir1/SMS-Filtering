package com.smsfiltering.sender.application;

import com.smsfiltering.sender.application.port.LookupSenderUseCase;
import com.smsfiltering.sender.application.port.SenderRepositoryPort;
import com.smsfiltering.sender.domain.SenderResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SenderLookupService implements LookupSenderUseCase {

    private final SenderRepositoryPort senderRepositoryPort;

    public SenderLookupService(SenderRepositoryPort senderRepositoryPort) {
        this.senderRepositoryPort = senderRepositoryPort;
    }

    @Override
    public SenderResult lookup(String senderValue) {
        if (senderValue == null || senderValue.isBlank()) {
            return SenderResult.notFound();
        }
        return senderRepositoryPort.findBySenderValueIgnoreCase(senderValue.trim())
                .orElseGet(SenderResult::notFound);
    }
}
