package com.smsfiltering.api.dto;

import java.util.List;

public record KeywordListResponse(
        int version,
        boolean upToDate,
        List<KeywordCategoryResponse> categories,
        List<TopicResponse> topics
) {
}
