package com.example.quanlybaotri.chat.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_read_states")
public class ChatReadState {
    @Id private UUID id;
    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "last_read_sequence", nullable = false) private long lastReadSequence;
    @Column(name = "read_at", nullable = false) private Instant readAt;

    protected ChatReadState() {}

    public ChatReadState(UUID conversationId, UUID userId) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.userId = userId;
        this.readAt = Instant.now();
    }

    public void advance(long sequence) {
        if (sequence > lastReadSequence) {
            lastReadSequence = sequence;
            readAt = Instant.now();
        }
    }

    public UUID getConversationId() { return conversationId; }
    public UUID getUserId() { return userId; }
    public long getLastReadSequence() { return lastReadSequence; }
    public Instant getReadAt() { return readAt; }
}
