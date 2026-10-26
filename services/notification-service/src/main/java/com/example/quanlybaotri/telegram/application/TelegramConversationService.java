package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.telegram.domain.TelegramConversation;
import com.example.quanlybaotri.telegram.domain.TelegramConversation.State;
import com.example.quanlybaotri.telegram.persistence.TelegramConversationRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class TelegramConversationService {
    private final TelegramConversationRepository conversations;
    private final TelegramProperties properties;
    private final ObjectMapper json;

    public TelegramConversationService(
        TelegramConversationRepository conversations,
        TelegramProperties properties,
        ObjectMapper json
    ) {
        this.conversations = conversations;
        this.properties = properties;
        this.json = json;
    }

    @Transactional
    public TelegramConversation get(UUID userId) {
        TelegramConversation conversation = conversations.findById(userId).orElse(null);
        if (conversation != null && conversation.expired()) {
            conversations.delete(conversation);
            return null;
        }
        return conversation;
    }

    @Transactional
    public TelegramConversation select(UUID userId, UUID ticketId) {
        TelegramConversation conversation = conversations.findById(userId)
            .orElseGet(() -> new TelegramConversation(userId, properties.getConversationTtl()));
        conversation.select(ticketId, properties.getConversationTtl());
        return conversations.save(conversation);
    }

    @Transactional
    public TelegramConversation step(UUID userId, State state, Map<String, String> draft) {
        TelegramConversation conversation = conversations.findById(userId)
            .orElseGet(() -> new TelegramConversation(userId, properties.getConversationTtl()));
        conversation.step(state, write(draft), properties.getConversationTtl());
        return conversations.save(conversation);
    }

    @Transactional
    public TelegramConversation menu(UUID userId) {
        TelegramConversation conversation = conversations.findById(userId)
            .orElseGet(() -> new TelegramConversation(userId, properties.getConversationTtl()));
        conversation.menu(properties.getConversationTtl());
        return conversations.save(conversation);
    }

    @Transactional
    public void clear(UUID userId) { conversations.deleteById(userId); }

    @Scheduled(fixedDelayString = "${app.telegram.conversation-cleanup-delay-ms:3600000}")
    @Transactional
    public void cleanupExpired() { conversations.deleteByExpiresAtBefore(Instant.now()); }

    public Map<String, String> draft(TelegramConversation conversation) {
        if (conversation == null || conversation.getDraftJson() == null) return new LinkedHashMap<>();
        try {
            return new LinkedHashMap<>(json.readValue(
                conversation.getDraftJson(),
                new TypeReference<Map<String, String>>() {}
            ));
        } catch (Exception invalid) {
            return new LinkedHashMap<>();
        }
    }

    private String write(Map<String, String> value) {
        try { return json.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception failure) { throw new IllegalStateException("Could not persist Telegram conversation", failure); }
    }
}
