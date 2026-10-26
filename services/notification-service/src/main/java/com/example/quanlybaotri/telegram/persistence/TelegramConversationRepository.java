package com.example.quanlybaotri.telegram.persistence;

import com.example.quanlybaotri.telegram.domain.TelegramConversation;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramConversationRepository extends JpaRepository<TelegramConversation, UUID> {
    long deleteByExpiresAtBefore(Instant instant);
}
