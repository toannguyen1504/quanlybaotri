package com.example.quanlybaotri.chat.application;

import com.example.quanlybaotri.chat.api.ChatViews;
import com.example.quanlybaotri.chat.persistence.ChatMessageRepository;
import com.example.quanlybaotri.chat.persistence.ChatReadStateRepository;
import com.example.quanlybaotri.config.RabbitConfig;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

@Component
public class ChatDeliveryConsumer {
    private final ObjectMapper json;
    private final SimpMessagingTemplate messaging;
    private final ChatMessageRepository messages;
    private final ChatReadStateRepository reads;
    public ChatDeliveryConsumer(ObjectMapper json, SimpMessagingTemplate messaging,
        ChatMessageRepository messages, ChatReadStateRepository reads) {
        this.json = json; this.messaging = messaging; this.messages = messages; this.reads = reads;
    }

    @RabbitListener(queues = RabbitConfig.CHAT_QUEUE)
    public void deliver(String payload) throws Exception {
        JsonNode data = json.readTree(payload).path("data");
        ChatViews.SocketEvent event = json.treeToValue(data.path("event"), ChatViews.SocketEvent.class);
        for (JsonNode recipient : data.path("recipients")) {
            UUID userId = UUID.fromString(recipient.asText());
            ChatViews.SocketEvent personalized = event;
            if ("MESSAGE_CREATED".equals(event.type()) && event.conversationId() != null) {
                long cursor = reads.findByConversationIdAndUserId(event.conversationId(), userId)
                    .map(state -> state.getLastReadSequence()).orElse(0L);
                long unread = messages.countUnread(event.conversationId(), userId, cursor);
                personalized = new ChatViews.SocketEvent(event.type(), event.conversationId(), event.ticket(),
                    event.message(), unread, event.throughSequence(), event.clientCommandId());
            }
            messaging.convertAndSendToUser(userId.toString(), "/exchange/amq.direct/chat-events", personalized);
        }
    }
}
