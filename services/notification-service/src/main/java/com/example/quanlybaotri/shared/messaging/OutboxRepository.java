package com.example.quanlybaotri.shared.messaging;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByOccurredAtAsc();
}
