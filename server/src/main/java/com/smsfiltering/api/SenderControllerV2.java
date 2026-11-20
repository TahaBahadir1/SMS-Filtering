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

/**
 * API v2 — Gönderen (sender) risk sorgulama ve sinyal kaydı uç noktaları.
 *
 * POST /api/v2/senders/lookup
 * POST /api/v2/senders/signals
 * Gizlilik: İstek gövdesi ve gönderen değeri kesinlikle loglanmaz.
 */
@RestController
@RequestMapping("/api/v2/senders")
public class SenderControllerV2 {

    private final LookupSenderUseCase lookupSenderUseCase;
    private final AddSignalUseCase addSignalUseCase;
    private final ApiMetricsCollector metricsCollector;

    public SenderControllerV2(LookupSenderUseCase lookupSenderUseCase, AddSignalUseCase addSignalUseCase) {
        this(lookupSenderUseCase, addSignalUseCase, new ApiMetricsCollector());
    }

    @Autowired
    public SenderControllerV2(
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
        // Gizlilik: İstek gövdesi ve gönderen değeri kesinlikle loglanmaz.
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
        // Gizlilik: SMS metni kesinlikle gönderilmez ve loglanmaz.
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
