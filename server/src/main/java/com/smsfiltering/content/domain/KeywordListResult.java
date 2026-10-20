package com.smsfiltering.content.domain;

import java.util.List;

public record KeywordListResult(
        int version,
        boolean upToDate,
        List<KeywordCategory> categories
) {
}
