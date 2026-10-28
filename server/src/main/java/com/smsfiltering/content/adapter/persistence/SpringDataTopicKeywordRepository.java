package com.smsfiltering.content.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataTopicKeywordRepository extends JpaRepository<TopicKeywordEntity, Long> {
}
