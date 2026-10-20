package com.smsfiltering.content.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SpringDataKeywordRepository extends JpaRepository<KeywordEntity, Long> {
    List<KeywordEntity> findByCategoryId(Short categoryId);
}
