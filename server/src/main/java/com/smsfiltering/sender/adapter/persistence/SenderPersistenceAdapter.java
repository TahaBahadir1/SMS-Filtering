package com.smsfiltering.sender.adapter.persistence;

import com.smsfiltering.sender.application.port.SenderRepositoryPort;
import com.smsfiltering.sender.domain.SenderResult;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SenderPersistenceAdapter implements SenderRepositoryPort {

    private final SpringDataSenderRepository senderRepository;

    public SenderPersistenceAdapter(SpringDataSenderRepository senderRepository) {
        this.senderRepository = senderRepository;
    }

    @Override
    public Optional<SenderResult> findBySenderValueIgnoreCase(String senderValue) {
        return senderRepository.findBySenderValueIgnoreCase(senderValue)
                .map(entity -> new SenderResult(
                        true,
                        entity.getRiskScore(),
                        entity.getRiskLevel(),
                        entity.getIsFinalized()
                ));
    }
}
