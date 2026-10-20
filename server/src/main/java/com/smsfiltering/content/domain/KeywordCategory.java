package com.smsfiltering.content.domain;

import java.util.List;

public record KeywordCategory(
        Short id,
        String name,
        List<String> keywords
) {
}
