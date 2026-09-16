package com.example.quanlybaotri.notification.persistence;

import com.example.quanlybaotri.notification.domain.Notification;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    List<Notification> findByUserIdAndReadAtIsNull(UUID userId);
}
