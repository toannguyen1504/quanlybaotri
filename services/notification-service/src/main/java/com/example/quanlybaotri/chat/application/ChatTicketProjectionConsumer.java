package com.example.quanlybaotri.chat.application;

import com.example.quanlybaotri.chat.api.ChatViews;
import com.example.quanlybaotri.chat.domain.ChatConversation;
import com.example.quanlybaotri.chat.persistence.ChatConversationRepository;
import com.example.quanlybaotri.config.RabbitConfig;
import com.example.quanlybaotri.identity.client.IdentityClient;
import java.util.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import tools.jackson.databind.*;

@Component
public class ChatTicketProjectionConsumer {
    private final ObjectMapper json;
    private final ChatConversationRepository conversations;
    private final IdentityClient identities;
    private final SimpMessagingTemplate messaging;

    public ChatTicketProjectionConsumer(ObjectMapper json, ChatConversationRepository conversations,
        IdentityClient identities, SimpMessagingTemplate messaging) {
        this.json = json; this.conversations = conversations; this.identities = identities; this.messaging = messaging;
    }

    @RabbitListener(queues = RabbitConfig.CHAT_TICKET_QUEUE)
    @Transactional
    public void update(String payload) throws Exception {
        JsonNode data = json.readTree(payload).path("data");
        UUID ticketId = UUID.fromString(data.path("ticketId").asText());
        ChatConversation conversation = conversations.findByTicketId(ticketId).orElse(null);
        if (conversation == null) return;
        UUID oldAssignee = conversation.getAssigneeId();
        UUID requester = uuid(data, "requesterId");
        UUID assignee = uuid(data, "assigneeId");
        String title = data.path("title").asText(conversation.getTicketTitle());
        String status = data.path("status").asText(conversation.getTicketStatus());
        String code = data.path("ticketCode").asText(conversation.getTicketCode());
        conversation.updateContext(code, title, status, requester, assignee);
        conversations.save(conversation);

        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(requester);
        if (assignee != null) recipients.add(assignee);
        recipients.addAll(identities.enabledByRoles(Set.of("ADMIN", "MANAGER")));
        boolean writable = !Set.of("CLOSED", "REJECTED", "CANCELLED").contains(status);
        ChatViews.TicketRef ticket = new ChatViews.TicketRef(ticketId, code, title, status, writable);
        ChatViews.SocketEvent changed = new ChatViews.SocketEvent("CONVERSATION_UPDATED", conversation.getId(), ticket, null, null, null, null);

        Runnable dispatch = () -> {
            recipients.forEach(id -> messaging.convertAndSendToUser(id.toString(), "/exchange/amq.direct/chat-events", changed));
            if (oldAssignee != null && !oldAssignee.equals(assignee) && !hasElevatedRole(oldAssignee)) {
                ChatViews.SocketEvent revoked = new ChatViews.SocketEvent("ACCESS_REVOKED", conversation.getId(), ticket, null, null, null, null);
                messaging.convertAndSendToUser(oldAssignee.toString(), "/exchange/amq.direct/chat-events", revoked);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatch.run(); }
            });
        } else dispatch.run();
    }

    private boolean hasElevatedRole(UUID id) {
        try {
            IdentityClient.UserRef user = identities.get(id);
            return user != null && user.roles() != null &&
                (user.roles().contains("ADMIN") || user.roles().contains("MANAGER"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private UUID uuid(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : UUID.fromString(value);
    }
}
