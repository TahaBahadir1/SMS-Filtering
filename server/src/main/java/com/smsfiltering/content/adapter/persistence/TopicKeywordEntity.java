package com.smsfiltering.content.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "topic_keywords")
public class TopicKeywordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "topic_id", nullable = false)
    private Short topicId;

    @Column(name = "keyword", length = 100, nullable = false)
    private String keyword;

    public TopicKeywordEntity() {
    }

    public TopicKeywordEntity(Long id, Short topicId, String keyword) {
        this.id = id;
        this.topicId = topicId;
        this.keyword = keyword;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Short getTopicId() {
        return topicId;
    }

    public void setTopicId(Short topicId) {
        this.topicId = topicId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
