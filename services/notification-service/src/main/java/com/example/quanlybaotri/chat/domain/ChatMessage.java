package com.example.quanlybaotri.chat.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chat_message_sequence")
    @SequenceGenerator(name = "chat_message_sequence", sequenceName = "chat_message_sequence", allocationSize = 1)
    @Column(name = "sequence_no")
    private Long sequence;
    @Column(nullable = false, unique = true) private UUID id;
    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(name = "sender_id", nullable = false) private UUID senderId;
    @Column(name = "client_message_id", nullable = false) private UUID clientMessageId;
    @Column(nullable = false, length = 5000) private String content;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ChatMessage() {}

    public ChatMessage(UUID conversationId, UUID senderId, UUID clientMessageId, String content) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getSequence() { return sequence; }
    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public UUID getSenderId() { return senderId; }
    public UUID getClientMessageId() { return clientMessageId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
