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

/**
 * API v2 — Gönderen (sender) risk sorgulama uç noktası.
 *
 * v1 controller'ı ({@link SenderController}) ile aynı iş mantığını kullanır;
 * yalnızca path /api/v2/senders/lookup olarak güncellendi.
 *
 * POST /api/v2/senders/lookup
 * Gizlilik: İstek gövdesi ve gönderen değeri kesinlikle loglanmaz.
 */
@RestController
@RequestMapping("/api/v2/senders")
public class SenderControllerV2 {

    private final LookupSenderUseCase lookupSenderUseCase;

    public SenderControllerV2(LookupSenderUseCase lookupSenderUseCase) {
        this.lookupSenderUseCase = lookupSenderUseCase;
    }

    @PostMapping("/lookup")
    public SenderLookupResponse lookup(@Valid @RequestBody SenderLookupRequest request) {
        // Gizlilik: İstek gövdesi ve gönderen değeri kesinlikle loglanmaz.
        SenderResult result = lookupSenderUseCase.lookup(request.sender());
        return new SenderLookupResponse(
                result.found(),
                result.riskScore(),
                result.riskLevel(),
                result.isFinalized()
        );
    }
}
