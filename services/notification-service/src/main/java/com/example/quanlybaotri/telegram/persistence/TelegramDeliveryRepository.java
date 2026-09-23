package com.example.quanlybaotri.telegram.persistence;

import com.example.quanlybaotri.telegram.domain.TelegramDelivery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramDeliveryRepository extends JpaRepository<TelegramDelivery, UUID> {
    List<TelegramDelivery> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
        TelegramDelivery.Status status,
        Instant now
    );

    List<TelegramDelivery> findByUserIdAndStatus(
        UUID userId,
        TelegramDelivery.Status status
    );
}
