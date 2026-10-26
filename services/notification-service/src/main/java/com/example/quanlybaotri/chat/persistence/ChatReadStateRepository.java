package com.example.quanlybaotri.chat.persistence;

import com.example.quanlybaotri.chat.domain.ChatReadState;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatReadStateRepository extends JpaRepository<ChatReadState, UUID> {
    Optional<ChatReadState> findByConversationIdAndUserId(UUID conversationId, UUID userId);
}
