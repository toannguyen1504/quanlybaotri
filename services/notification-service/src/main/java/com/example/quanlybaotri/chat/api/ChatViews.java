package com.example.quanlybaotri.chat.api;

import java.time.Instant;
import java.util.*;

public final class ChatViews {
    private ChatViews() {}

    public record TicketRef(UUID id, String code, String title, String status, boolean writable) {}
    public record Sender(UUID id, String fullName, Set<String> roles) {}
    public record Message(UUID id, long sequence, UUID clientMessageId, Sender sender, String content, Instant createdAt) {}
    public record Conversation(UUID id, TicketRef ticket, Message lastMessage, long unreadCount) {}
    public record ConversationPage(List<Conversation> content, String nextCursor) {}
    public record ConversationSummary(long total, long active, long unreadConversations, long unreadMessages, long closed, long messagesInPeriod) {}
    public record MessagePage(TicketRef ticket, List<Message> content, Long nextBeforeSequence) {}
    public record UnreadCount(long count) {}
    public record SocketEvent(String type, UUID conversationId, TicketRef ticket, Message message,
        Long unreadCount, Long throughSequence, String clientCommandId) {}
    public record CommandError(String type, String code, String message, UUID ticketId,
        UUID clientMessageId, String clientCommandId) {}
}
