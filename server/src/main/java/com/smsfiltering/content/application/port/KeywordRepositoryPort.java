package com.smsfiltering.content.application.port;

import com.smsfiltering.content.domain.KeywordCategory;
import java.util.List;

public interface KeywordRepositoryPort {
    int getLatestVersion();
    List<KeywordCategory> getAllCategoriesWithKeywords();
}
