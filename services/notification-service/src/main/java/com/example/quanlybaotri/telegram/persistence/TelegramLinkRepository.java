package com.example.quanlybaotri.telegram.persistence;

import com.example.quanlybaotri.telegram.domain.TelegramLink;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramLinkRepository extends JpaRepository<TelegramLink, UUID> {
    Optional<TelegramLink> findByTelegramUserId(long telegramUserId);

    Optional<TelegramLink> findByUserIdAndActiveTrue(UUID userId);
}
