package com.smsfiltering.content.adapter.persistence;

import com.smsfiltering.content.application.port.KeywordRepositoryPort;
import com.smsfiltering.content.domain.KeywordCategory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KeywordPersistenceAdapter implements KeywordRepositoryPort {

    private final SpringDataKeywordCategoryRepository categoryRepository;
    private final SpringDataKeywordRepository keywordRepository;
    private final SpringDataKeywordListVersionRepository versionRepository;

    public KeywordPersistenceAdapter(
            SpringDataKeywordCategoryRepository categoryRepository,
            SpringDataKeywordRepository keywordRepository,
            SpringDataKeywordListVersionRepository versionRepository) {
        this.categoryRepository = categoryRepository;
        this.keywordRepository = keywordRepository;
        this.versionRepository = versionRepository;
    }

    @Override
    public int getLatestVersion() {
        return versionRepository.findTopByOrderByVersionDesc()
                .map(KeywordListVersionEntity::getVersion)
                .orElse(0);
    }

    @Override
    public List<KeywordCategory> getAllCategoriesWithKeywords() {
        List<KeywordCategoryEntity> categories = categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        List<KeywordEntity> keywords = keywordRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));

        Map<Short, List<String>> keywordsByCategoryId = keywords.stream()
                .collect(Collectors.groupingBy(
                        KeywordEntity::getCategoryId,
                        Collectors.mapping(KeywordEntity::getKeyword, Collectors.toList())
                ));

        return categories.stream()
                .map(cat -> new KeywordCategory(
                        cat.getId(),
                        cat.getName(),
                        keywordsByCategoryId.getOrDefault(cat.getId(), List.of())
                ))
                .toList();
    }
}
