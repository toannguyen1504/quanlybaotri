package com.example.quanlybaotri.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.chat.api.ChatViews;
import com.example.quanlybaotri.chat.persistence.ChatMessageRepository;
import com.example.quanlybaotri.chat.persistence.ChatReadStateRepository;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

class ChatDeliveryConsumerTest {
    @Test
    void addsRecipientSpecificUnreadCountBeforeWebSocketDelivery() throws Exception {
        ObjectMapper json = new ObjectMapper();
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ChatReadStateRepository reads = mock(ChatReadStateRepository.class);
        ChatDeliveryConsumer consumer = new ChatDeliveryConsumer(json, messaging, messages, reads);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        ChatViews.SocketEvent event = new ChatViews.SocketEvent("MESSAGE_CREATED", conversationId,
            new ChatViews.TicketRef(ticketId, "TK-1", "Printer", "IN_PROGRESS", true),
            null, null, null, null);
        String payload = json.writeValueAsString(Map.of("data", Map.of(
            "recipients", Set.of(userId),
            "event", event
        )));
        when(reads.findByConversationIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());
        when(messages.countUnread(conversationId, userId, 0)).thenReturn(4L);

        consumer.deliver(payload);

        ArgumentCaptor<Object> delivered = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSendToUser(eq(userId.toString()),
            eq("/exchange/amq.direct/chat-events"), delivered.capture());
        assertThat(delivered.getValue()).isInstanceOf(ChatViews.SocketEvent.class);
        assertThat(((ChatViews.SocketEvent) delivered.getValue()).unreadCount()).isEqualTo(4);
    }
}
