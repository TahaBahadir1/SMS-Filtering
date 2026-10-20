package com.smsfiltering.api.dto;

import java.util.List;

public record KeywordCategoryResponse(
        Short id,
        String name,
        List<String> keywords
) {
}
