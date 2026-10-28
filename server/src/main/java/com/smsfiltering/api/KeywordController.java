package com.smsfiltering.api;

import com.smsfiltering.api.dto.KeywordCategoryResponse;
import com.smsfiltering.api.dto.KeywordListResponse;
import com.smsfiltering.api.dto.TopicResponse;
import com.smsfiltering.content.application.port.GetKeywordsUseCase;
import com.smsfiltering.content.domain.KeywordListResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/keywords")
public class KeywordController {

    private final GetKeywordsUseCase getKeywordsUseCase;

    public KeywordController(GetKeywordsUseCase getKeywordsUseCase) {
        this.getKeywordsUseCase = getKeywordsUseCase;
    }

    @GetMapping
    public KeywordListResponse getKeywords(@RequestParam(name = "version", required = false, defaultValue = "0") int version) {
        KeywordListResult result = getKeywordsUseCase.getKeywords(version);
        return new KeywordListResponse(
                result.version(),
                result.upToDate(),
                result.categories().stream()
                        .map(cat -> new KeywordCategoryResponse(cat.id(), cat.name(), cat.keywords()))
                        .toList(),
                result.topics().stream()
                        .map(topic -> new TopicResponse(topic.id(), topic.name(), topic.keywords()))
                        .toList()
        );
    }
}
