package com.smsfiltering.sender.application.port;

import com.smsfiltering.sender.domain.Sender;
import java.math.BigDecimal;

public interface AddSignalUseCase {
    Sender addSignal(String senderValue, String source, BigDecimal score);
}
