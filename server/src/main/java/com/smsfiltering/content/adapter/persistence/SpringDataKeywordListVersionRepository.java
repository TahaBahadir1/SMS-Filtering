package com.smsfiltering.content.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpringDataKeywordListVersionRepository extends JpaRepository<KeywordListVersionEntity, Integer> {
    Optional<KeywordListVersionEntity> findTopByOrderByVersionDesc();
}
