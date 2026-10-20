package com.smsfiltering.content.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataKeywordCategoryRepository extends JpaRepository<KeywordCategoryEntity, Short> {
}
