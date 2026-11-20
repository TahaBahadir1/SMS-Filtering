package com.smsfiltering.api;

import com.smsfiltering.api.dto.SenderLookupRequest;
import com.smsfiltering.api.dto.SenderLookupResponse;
import com.smsfiltering.api.dto.SenderSignalRequest;
import com.smsfiltering.api.dto.SenderSignalResponse;
import com.smsfiltering.api.security.ApiMetricsCollector;
import com.smsfiltering.sender.application.port.AddSignalUseCase;
import com.smsfiltering.sender.application.port.LookupSenderUseCase;
import com.smsfiltering.sender.domain.Sender;
import com.smsfiltering.sender.domain.SenderResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/senders")
public class SenderController {

    private final LookupSenderUseCase lookupSenderUseCase;
    private final AddSignalUseCase addSignalUseCase;
    private final ApiMetricsCollector metricsCollector;

    public SenderController(LookupSenderUseCase lookupSenderUseCase, AddSignalUseCase addSignalUseCase) {
        this(lookupSenderUseCase, addSignalUseCase, new ApiMetricsCollector());
    }

    @Autowired
    public SenderController(
            LookupSenderUseCase lookupSenderUseCase,
            AddSignalUseCase addSignalUseCase,
            ApiMetricsCollector metricsCollector
    ) {
        this.lookupSenderUseCase = lookupSenderUseCase;
        this.addSignalUseCase = addSignalUseCase;
        this.metricsCollector = metricsCollector;
    }

    @PostMapping("/lookup")
    public SenderLookupResponse lookup(@Valid @RequestBody SenderLookupRequest request) {
        // Gizlilik: İstek govdesi ve gonderen degeri kesinlikle loglanmaz.
        metricsCollector.recordLookup();
        SenderResult result = lookupSenderUseCase.lookup(request.sender());
        return new SenderLookupResponse(
                result.found(),
                result.riskScore(),
                result.riskLevel(),
                result.isFinalized()
        );
    }

    @PostMapping("/signals")
    public SenderSignalResponse addSignal(@Valid @RequestBody SenderSignalRequest request) {
        // Gizlilik: SMS metni kesinlikle gonderilmez ve loglanmaz. Sadece gonderen kimligi ve risk sinyali kaydedilir.
        metricsCollector.recordSignal();
        Sender sender = addSignalUseCase.addSignal(request.sender(), request.source(), request.score());
        return new SenderSignalResponse(
                sender.getSenderValue(),
                sender.getRiskScore(),
                sender.getRiskLevel().getValue(),
                sender.isFinalized()
        );
    }
}
