package com.smsfiltering.sender.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SpringDataSenderSignalRepository extends JpaRepository<SenderSignalEntity, Long> {
    List<SenderSignalEntity> findBySenderIdOrderByCreatedAtAsc(Long senderId);
}
