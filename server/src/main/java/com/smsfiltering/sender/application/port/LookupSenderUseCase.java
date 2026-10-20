package com.smsfiltering.sender.application.port;

import com.smsfiltering.sender.domain.SenderResult;

public interface LookupSenderUseCase {
    SenderResult lookup(String senderValue);
}
