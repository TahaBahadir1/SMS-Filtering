package com.smsfiltering.content.domain;

import java.util.List;

public record Topic(
        Short id,
        String name,
        List<String> keywords
) {
}
