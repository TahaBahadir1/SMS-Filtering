package com.smsfiltering.content.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "keyword_list_version")
public class KeywordListVersionEntity {

    @Id
    @Column(name = "version")
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public KeywordListVersionEntity() {
    }

    public KeywordListVersionEntity(Integer version, OffsetDateTime updatedAt) {
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
