package com.smsfiltering.sender.adapter.persistence;

import com.smsfiltering.sender.application.port.SenderRepositoryPort;
import com.smsfiltering.sender.domain.RiskLevel;
import com.smsfiltering.sender.domain.Sender;
import com.smsfiltering.sender.domain.SenderResult;
import com.smsfiltering.sender.domain.SenderSignal;
import com.smsfiltering.sender.domain.SignalSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SenderPersistenceAdapter implements SenderRepositoryPort {

    private final SpringDataSenderRepository senderRepository;
    private final SpringDataSenderSignalRepository signalRepository;

    public SenderPersistenceAdapter(
            SpringDataSenderRepository senderRepository,
            SpringDataSenderSignalRepository signalRepository) {
        this.senderRepository = senderRepository;
        this.signalRepository = signalRepository;
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

    @Override
    public Optional<Sender> findDomainBySenderValueIgnoreCase(String senderValue) {
        return senderRepository.findBySenderValueIgnoreCase(senderValue)
                .map(entity -> {
                    List<SenderSignalEntity> signalEntities = signalRepository.findBySenderIdOrderByCreatedAtAsc(entity.getId());
                    List<SenderSignal> signals = signalEntities.stream()
                            .map(se -> new SenderSignal(
                                    se.getId(),
                                    SignalSource.fromValue(se.getSource()),
                                    se.getScore(),
                                    se.getCreatedAt()
                            ))
                            .toList();

                    return new Sender(
                            entity.getId(),
                            entity.getSenderValue(),
                            entity.getRiskScore(),
                            RiskLevel.fromValue(entity.getRiskLevel()),
                            entity.getIsFinalized(),
                            entity.getFirstSeen(),
                            entity.getLastUpdated(),
                            signals
                    );
                });
    }

    @Override
    public Sender save(Sender sender) {
        SenderEntity senderEntity;
        if (sender.getId() != null) {
            senderEntity = senderRepository.findById(sender.getId())
                    .orElseGet(SenderEntity::new);
        } else {
            senderEntity = new SenderEntity();
            senderEntity.setSenderValue(sender.getSenderValue());
            senderEntity.setFirstSeen(sender.getFirstSeen());
        }

        senderEntity.setSenderValue(sender.getSenderValue());
        senderEntity.setRiskScore(sender.getRiskScore());
        senderEntity.setRiskLevel(sender.getRiskLevel().getValue());
        senderEntity.setIsFinalized(sender.isFinalized());
        senderEntity.setLastUpdated(sender.getLastUpdated());
        if (senderEntity.getFirstSeen() == null) {
            senderEntity.setFirstSeen(sender.getFirstSeen());
        }

        SenderEntity savedEntity = senderRepository.save(senderEntity);
        sender.setId(savedEntity.getId());

        // Save new signals
        for (SenderSignal signal : sender.getSignals()) {
            if (signal.getId() == null) {
                SenderSignalEntity signalEntity = new SenderSignalEntity(
                        null,
                        savedEntity.getId(),
                        signal.getSource().getValue(),
                        signal.getScore(),
                        signal.getCreatedAt()
                );
                signalRepository.save(signalEntity);
            }
        }

        return sender;
    }
}
