package com.smsfiltering.sender.application.port;

import com.smsfiltering.sender.domain.Sender;
import com.smsfiltering.sender.domain.SenderResult;
import java.util.Optional;

public interface SenderRepositoryPort {
    Optional<SenderResult> findBySenderValueIgnoreCase(String senderValue);
    Optional<Sender> findDomainBySenderValueIgnoreCase(String senderValue);
    Sender save(Sender sender);
}
