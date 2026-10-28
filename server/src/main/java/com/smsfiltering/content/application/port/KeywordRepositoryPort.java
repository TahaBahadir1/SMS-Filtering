package com.smsfiltering.content.application.port;

import com.smsfiltering.content.domain.KeywordCategory;
import com.smsfiltering.content.domain.Topic;
import java.util.List;

public interface KeywordRepositoryPort {
    int getLatestVersion();
    List<KeywordCategory> getAllCategoriesWithKeywords();
    List<Topic> getAllTopicsWithKeywords();
}
