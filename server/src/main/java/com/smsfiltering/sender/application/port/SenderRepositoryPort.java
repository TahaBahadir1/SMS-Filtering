package com.smsfiltering.sender.application.port;

import com.smsfiltering.sender.domain.SenderResult;
import java.util.Optional;

public interface SenderRepositoryPort {
    Optional<SenderResult> findBySenderValueIgnoreCase(String senderValue);
}
