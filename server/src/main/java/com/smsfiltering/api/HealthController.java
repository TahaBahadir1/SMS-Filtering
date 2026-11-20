package com.smsfiltering.api;

import com.smsfiltering.api.security.ApiMetricsCollector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sunucu sağlık ve gizlilik odaklı anonim metrik uç noktası.
 * GET /api/v1/health ve GET /api/v2/health
 */
@RestController
@RequestMapping({"/api/v1", "/api/v2"})
public class HealthController {

    private final ApiMetricsCollector metricsCollector;

    public HealthController(ApiMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "uptimeSeconds", metricsCollector.getUptimeSeconds(),
                "totalLookups", metricsCollector.getTotalLookups(),
                "totalSignals", metricsCollector.getTotalSignals(),
                "totalKeywordSyncs", metricsCollector.getTotalKeywordSyncs(),
                "totalRateLimited", metricsCollector.getTotalRateLimited()
        );
    }
}
