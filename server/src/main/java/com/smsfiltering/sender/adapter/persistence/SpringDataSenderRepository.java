package com.smsfiltering.sender.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpringDataSenderRepository extends JpaRepository<SenderEntity, Long> {
    Optional<SenderEntity> findBySenderValueIgnoreCase(String senderValue);
}
