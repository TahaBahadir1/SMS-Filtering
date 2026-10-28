package com.smsfiltering.api.dto;

import java.util.List;

public record TopicResponse(
        Short id,
        String name,
        List<String> keywords
) {
}
