package com.smsfiltering.api;

import com.smsfiltering.api.dto.SenderLookupRequest;
import com.smsfiltering.api.dto.SenderLookupResponse;
import com.smsfiltering.sender.application.port.LookupSenderUseCase;
import com.smsfiltering.sender.domain.SenderResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/senders")
public class SenderController {

    private final LookupSenderUseCase lookupSenderUseCase;

    public SenderController(LookupSenderUseCase lookupSenderUseCase) {
        this.lookupSenderUseCase = lookupSenderUseCase;
    }

    @PostMapping("/lookup")
    public SenderLookupResponse lookup(@Valid @RequestBody SenderLookupRequest request) {
        // Gizlilik: İstek govdesi ve gonderen degeri kesinlikle loglanmaz.
        SenderResult result = lookupSenderUseCase.lookup(request.sender());
        return new SenderLookupResponse(
                result.found(),
                result.riskScore(),
                result.riskLevel(),
                result.isFinalized()
        );
    }
}
