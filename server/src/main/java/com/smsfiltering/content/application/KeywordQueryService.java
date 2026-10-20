package com.smsfiltering.content.application;

import com.smsfiltering.content.application.port.GetKeywordsUseCase;
import com.smsfiltering.content.application.port.KeywordRepositoryPort;
import com.smsfiltering.content.domain.KeywordListResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@Transactional(readOnly = true)
public class KeywordQueryService implements GetKeywordsUseCase {

    private final KeywordRepositoryPort keywordRepositoryPort;

    public KeywordQueryService(KeywordRepositoryPort keywordRepositoryPort) {
        this.keywordRepositoryPort = keywordRepositoryPort;
    }

    @Override
    public KeywordListResult getKeywords(int clientVersion) {
        int latestVersion = keywordRepositoryPort.getLatestVersion();
        if (clientVersion == latestVersion) {
            return new KeywordListResult(latestVersion, true, Collections.emptyList());
        }
        return new KeywordListResult(latestVersion, false, keywordRepositoryPort.getAllCategoriesWithKeywords());
    }
}
