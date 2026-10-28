package com.smsfiltering.content.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataTopicRepository extends JpaRepository<TopicEntity, Short> {
}
