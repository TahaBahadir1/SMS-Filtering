package com.smsfiltering.content.application.port;

import com.smsfiltering.content.domain.KeywordListResult;

public interface GetKeywordsUseCase {
    KeywordListResult getKeywords(int clientVersion);
}
