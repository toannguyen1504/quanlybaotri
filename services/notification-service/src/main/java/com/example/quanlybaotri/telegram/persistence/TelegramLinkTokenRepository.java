package com.example.quanlybaotri.telegram.persistence;

import com.example.quanlybaotri.telegram.domain.TelegramLinkToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TelegramLinkTokenRepository extends JpaRepository<TelegramLinkToken, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TelegramLinkToken t where t.tokenHash = :tokenHash")
    Optional<TelegramLinkToken> findLockedByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from TelegramLinkToken t where t.userId = :userId")
    void deleteByUserId(UUID userId);

    void deleteByExpiresAtBefore(Instant cutoff);
}
