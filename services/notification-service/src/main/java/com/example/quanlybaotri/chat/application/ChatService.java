package com.example.quanlybaotri.chat.application;

import com.example.quanlybaotri.chat.api.ChatViews;
import com.example.quanlybaotri.chat.domain.*;
import com.example.quanlybaotri.chat.persistence.*;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.messaging.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChatService {
    public static final String MESSAGE_CREATED = "notification.chat.message-created";
    public static final String READ_CHANGED = "notification.chat.read-changed";
    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ChatReadStateRepository reads;
    private final OutboxRepository outbox;
    private final ChatTicketClient tickets;
    private final IdentityClient identities;
    private final ChatRateLimiter limiter;
    private final ObjectMapper json;

    public ChatService(ChatConversationRepository conversations, ChatMessageRepository messages,
        ChatReadStateRepository reads, OutboxRepository outbox, ChatTicketClient tickets,
        IdentityClient identities, ChatRateLimiter limiter, ObjectMapper json) {
        this.conversations = conversations; this.messages = messages; this.reads = reads;
        this.outbox = outbox; this.tickets = tickets; this.identities = identities;
        this.limiter = limiter; this.json = json;
    }

    @Transactional
    public ChatViews.Message send(UUID actorId, Set<String> roles, String userToken,
        UUID ticketId, UUID clientMessageId, String rawContent) {
        if (ticketId == null || clientMessageId == null) throw validation("Thiếu ticketId hoặc clientMessageId");
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isEmpty() || content.length() > 5000) throw validation("Tin nhắn phải có từ 1 đến 5000 ký tự");
        ChatTicketClient.ChatContext context = tickets.get(ticketId, userToken);
        if (!context.writable()) throw new ApiException(HttpStatus.CONFLICT, "CHAT_READ_ONLY", "Phiếu đã kết thúc, phòng chat chỉ được đọc");
        Optional<ChatMessage> duplicate = messages.findBySenderIdAndClientMessageId(actorId, clientMessageId);
        if (duplicate.isPresent()) return view(duplicate.get(), identities.resolve(Set.of(actorId)));
        limiter.check(actorId);
        ChatConversation conversation = upsertContext(context);
        ChatMessage message = messages.saveAndFlush(new ChatMessage(conversation.getId(), actorId, clientMessageId, content));
        conversation.recordMessage(message.getSequence(), content, message.getCreatedAt());
        Map<UUID, IdentityClient.UserRef> senders = identities.resolve(Set.of(actorId));
        ChatViews.Message view = view(message, senders);
        Set<UUID> recipients = recipients(context, actorId);
        saveOutbox(MESSAGE_CREATED, conversation.getId(), Map.of(
            "recipients", recipients,
            "event", new ChatViews.SocketEvent("MESSAGE_CREATED", conversation.getId(), ticket(context), view, null, null, null)
        ));
        return view;
    }

    @Transactional
    public long markRead(UUID actorId, String userToken, UUID ticketId, long throughSequence, String clientCommandId) {
        ChatTicketClient.ChatContext context = tickets.get(ticketId, userToken);
        ChatConversation conversation = conversations.findByTicketId(ticketId).orElse(null);
        if (conversation == null) return 0;
        long maximum = conversation.getLastMessageSequence() == null ? 0 : conversation.getLastMessageSequence();
        long target = Math.max(0, Math.min(throughSequence, maximum));
        ChatReadState state = reads.findByConversationIdAndUserId(conversation.getId(), actorId)
            .orElseGet(() -> new ChatReadState(conversation.getId(), actorId));
        state.advance(target);
        reads.save(state);
        long cursor = state.getLastReadSequence();
        saveOutbox(READ_CHANGED, conversation.getId(), Map.of(
            "recipients", Set.of(actorId),
            "event", new ChatViews.SocketEvent("READ_UPDATED", conversation.getId(), ticket(context), null, 0L, cursor, clientCommandId)
        ));
        return cursor;
    }

    @Transactional(readOnly = true)
    public ChatViews.MessagePage history(UUID actorId, String userToken, UUID ticketId, Long before, int requestedSize) {
        ChatTicketClient.ChatContext context = tickets.get(ticketId, userToken);
        ChatConversation conversation = conversations.findByTicketId(ticketId).orElse(null);
        if (conversation == null) return new ChatViews.MessagePage(ticket(context), List.of(), null);
        int size = Math.max(1, Math.min(requestedSize, 50));
        List<ChatMessage> found = messages.history(conversation.getId(), before, PageRequest.of(0, size + 1));
        boolean more = found.size() > size;
        if (more) found = new ArrayList<>(found.subList(0, size));
        Collections.reverse(found);
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(found.stream().map(ChatMessage::getSenderId).toList());
        List<ChatViews.Message> result = found.stream().map(message -> view(message, users)).toList();
        Long next = more && !found.isEmpty() ? found.get(0).getSequence() : null;
        return new ChatViews.MessagePage(ticket(context), result, next);
    }

    @Transactional
    public ChatViews.ConversationPage inbox(UUID actorId, Set<String> roles, String userToken, String cursor, int requestedSize) {
        return inbox(actorId, roles, userToken, cursor, null, "ALL", false, requestedSize);
    }

    @Transactional
    public ChatViews.ConversationPage inbox(UUID actorId, Set<String> roles, String userToken, String cursor, String query, String bucket, boolean unreadOnly, int requestedSize) {
        int size = Math.max(1, Math.min(requestedSize, 50));
        CursorKey cursorKey = decodeCursor(cursor);
        boolean elevated = roles.contains("ROLE_ADMIN") || roles.contains("ROLE_MANAGER");
        PageRequest page = PageRequest.of(0, size * 3 + 1);
        String normalizedBucket = Set.of("ALL", "ACTIVE", "CLOSED").contains(bucket) ? bucket : "ALL";
        String normalizedQuery = query == null ? "" : query.trim();
        List<ChatConversation> candidates = cursorKey == null
            ? conversations.searchInboxFirstPage(actorId, elevated, normalizedQuery, normalizedBucket, unreadOnly, page)
            : cursorKey.id() == null
                ? conversations.searchInboxBefore(actorId, elevated, cursorKey.at(), normalizedQuery, normalizedBucket, unreadOnly, page)
                : conversations.searchInboxAfterCursor(actorId, elevated, cursorKey.at(), cursorKey.id(), normalizedQuery, normalizedBucket, unreadOnly, page);
        Map<UUID, ChatTicketClient.ChatContext> allowed = new LinkedHashMap<>();
        tickets.resolve(candidates.stream().map(ChatConversation::getTicketId).toList(), userToken)
            .forEach(context -> allowed.put(context.ticketId(), context));
        List<ChatConversation> visible = candidates.stream().filter(c -> allowed.containsKey(c.getTicketId())).limit(size + 1L).toList();
        boolean more = visible.size() > size;
        if (more) visible = visible.subList(0, size);
        Set<UUID> senderIds = new LinkedHashSet<>();
        List<ChatMessage> lastMessages = new ArrayList<>();
        for (ChatConversation conversation : visible) {
            if (conversation.getLastMessageSequence() != null) messages.findById(conversation.getLastMessageSequence()).ifPresent(message -> {
                lastMessages.add(message); senderIds.add(message.getSenderId());
            });
        }
        Map<Long, ChatMessage> bySequence = new HashMap<>();
        lastMessages.forEach(message -> bySequence.put(message.getSequence(), message));
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(senderIds);
        Map<UUID, Long> unreadByConversation = visible.isEmpty() ? Map.of() : messages
            .countUnreadByConversation(visible.stream().map(ChatConversation::getId).toList(), actorId)
            .stream()
            .collect(java.util.stream.Collectors.toMap(
                ChatMessageRepository.ConversationUnread::getConversationId,
                ChatMessageRepository.ConversationUnread::getUnreadCount
            ));
        List<ChatViews.Conversation> content = visible.stream().map(conversation -> {
            ChatTicketClient.ChatContext context = allowed.get(conversation.getTicketId());
            long unread = unreadByConversation.getOrDefault(conversation.getId(), 0L);
            ChatMessage last = bySequence.get(conversation.getLastMessageSequence());
            return new ChatViews.Conversation(conversation.getId(), ticket(context), last == null ? null : view(last, users), unread);
        }).toList();
        ChatConversation lastVisible = visible.isEmpty() ? null : visible.get(visible.size() - 1);
        String next = more ? encodeCursor(lastVisible.getLastMessageAt(), lastVisible.getId()) : null;
        return new ChatViews.ConversationPage(content, next);
    }

    @Transactional(readOnly = true)
    public long unread(UUID actorId, Set<String> roles) {
        boolean elevated = roles.contains("ROLE_ADMIN") || roles.contains("ROLE_MANAGER");
        return messages.countUnreadVisible(actorId, elevated);
    }

    @Transactional(readOnly = true)
    public ChatViews.ConversationSummary summary(UUID actorId, Set<String> roles, Instant from, Instant to) {
        if (!from.isBefore(to)) throw validation("Khoáº£ng ngÃ y tá»•ng há»£p khÃ´ng há»£p lá»‡");
        boolean elevated = roles.contains("ROLE_ADMIN") || roles.contains("ROLE_MANAGER");
        return new ChatViews.ConversationSummary(
            conversations.countVisibleByBucket(actorId, elevated, "ALL"),
            conversations.countVisibleByBucket(actorId, elevated, "ACTIVE"),
            conversations.countUnreadConversations(actorId, elevated),
            messages.countUnreadVisible(actorId, elevated),
            conversations.countVisibleByBucket(actorId, elevated, "CLOSED"),
            messages.countVisibleSince(actorId, elevated, from, to)
        );
    }

    private ChatConversation upsertContext(ChatTicketClient.ChatContext context) {
        ChatConversation conversation = conversations.findByTicketId(context.ticketId()).orElseGet(() ->
            new ChatConversation(context.ticketId(), context.ticketCode(), context.ticketTitle(), context.status(), context.requesterId(), context.assigneeId()));
        conversation.updateContext(context.ticketCode(), context.ticketTitle(), context.status(), context.requesterId(), context.assigneeId());
        return conversations.save(conversation);
    }

    private Set<UUID> recipients(ChatTicketClient.ChatContext context, UUID sender) {
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(sender); recipients.add(context.requesterId());
        if (context.assigneeId() != null) recipients.add(context.assigneeId());
        recipients.addAll(identities.enabledByRoles(Set.of("ADMIN", "MANAGER")));
        return recipients;
    }

    private ChatViews.Message view(ChatMessage message, Map<UUID, IdentityClient.UserRef> users) {
        IdentityClient.UserRef user = users.get(message.getSenderId());
        ChatViews.Sender sender = new ChatViews.Sender(message.getSenderId(), user == null ? "Người dùng" : user.fullName(),
            user == null || user.roles() == null ? Set.of() : user.roles());
        return new ChatViews.Message(message.getId(), message.getSequence(), message.getClientMessageId(), sender,
            message.getContent(), message.getCreatedAt());
    }

    private ChatViews.TicketRef ticket(ChatTicketClient.ChatContext context) {
        return new ChatViews.TicketRef(context.ticketId(), context.ticketCode(), context.ticketTitle(), context.status(), context.writable());
    }

    private void saveOutbox(String type, UUID aggregateId, Object data) {
        try {
            UUID eventId = UUID.randomUUID();
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", eventId); envelope.put("eventType", type); envelope.put("schemaVersion", 1);
            envelope.put("aggregateType", "chat-conversation"); envelope.put("aggregateId", aggregateId);
            envelope.put("occurredAt", Instant.now()); envelope.put("producer", "notification-service");
            envelope.put("correlationId", eventId); envelope.put("data", data);
            outbox.save(new OutboxEvent(eventId, "CHAT", aggregateId, type, type, json.writeValueAsString(envelope)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize chat event", error);
        }
    }

    private ApiException validation(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "CHAT_VALIDATION", message); }
    private String encodeCursor(Instant value, UUID id) { return Base64.getUrlEncoder().withoutPadding().encodeToString((value + "|" + id).getBytes(StandardCharsets.UTF_8)); }
    private CursorKey decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = decoded.indexOf('|');
            return separator < 0
                ? new CursorKey(Instant.parse(decoded), null)
                : new CursorKey(Instant.parse(decoded.substring(0, separator)), UUID.fromString(decoded.substring(separator + 1)));
        }
        catch (RuntimeException invalid) { throw validation("Cursor không hợp lệ"); }
    }
    private record CursorKey(Instant at, UUID id) {}
}
