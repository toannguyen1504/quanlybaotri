package com.example.quanlybaotri.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.chat.domain.ChatConversation;
import com.example.quanlybaotri.chat.domain.ChatMessage;
import com.example.quanlybaotri.chat.domain.ChatReadState;
import com.example.quanlybaotri.chat.persistence.ChatConversationRepository;
import com.example.quanlybaotri.chat.persistence.ChatMessageRepository;
import com.example.quanlybaotri.chat.persistence.ChatReadStateRepository;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.messaging.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

class ChatServiceTest {
    private ChatConversationRepository conversations;
    private ChatMessageRepository messages;
    private ChatReadStateRepository reads;
    private OutboxRepository outbox;
    private ChatTicketClient tickets;
    private IdentityClient identities;
    private ChatRateLimiter limiter;
    private ObjectMapper json;
    private ChatService service;

    @BeforeEach
    void setUp() {
        conversations = mock(ChatConversationRepository.class);
        messages = mock(ChatMessageRepository.class);
        reads = mock(ChatReadStateRepository.class);
        outbox = mock(OutboxRepository.class);
        tickets = mock(ChatTicketClient.class);
        identities = mock(IdentityClient.class);
        limiter = mock(ChatRateLimiter.class);
        json = mock(ObjectMapper.class);
        service = new ChatService(conversations, messages, reads, outbox, tickets, identities, limiter, json);
    }

    @Test
    void rejectsInvalidContentBeforeCallingDependencies() {
        assertThatThrownBy(() -> service.send(UUID.randomUUID(), Set.of("ROLE_REQUESTER"), "token",
            UUID.randomUUID(), UUID.randomUUID(), "   "))
            .isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("CHAT_VALIDATION"));

        verifyNoInteractions(tickets, messages, limiter, outbox);
    }

    @Test
    void checksCurrentWriteAccessBeforeReturningAnIdempotentMessage() {
        UUID actorId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        when(tickets.get(ticketId, "token")).thenReturn(context(ticketId, actorId, null, false));

        assertThatThrownBy(() -> service.send(actorId, Set.of("ROLE_REQUESTER"), "token",
            ticketId, commandId, "hello"))
            .isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("CHAT_READ_ONLY"));

        verifyNoInteractions(messages, limiter, outbox);
    }

    @Test
    void idempotentRetryDoesNotConsumeRateLimitOrCreateAnotherOutboxEvent() {
        UUID actorId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID clientMessageId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        ChatMessage duplicate = mock(ChatMessage.class);
        when(duplicate.getId()).thenReturn(messageId);
        when(duplicate.getSequence()).thenReturn(17L);
        when(duplicate.getClientMessageId()).thenReturn(clientMessageId);
        when(duplicate.getSenderId()).thenReturn(actorId);
        when(duplicate.getContent()).thenReturn("hello");
        when(duplicate.getCreatedAt()).thenReturn(Instant.now());
        when(tickets.get(ticketId, "token")).thenReturn(context(ticketId, actorId, null, true));
        when(messages.findBySenderIdAndClientMessageId(actorId, clientMessageId)).thenReturn(Optional.of(duplicate));
        when(identities.resolve(Set.of(actorId))).thenReturn(Map.of());

        var result = service.send(actorId, Set.of("ROLE_REQUESTER"), "token", ticketId, clientMessageId, "hello");

        assertThat(result.id()).isEqualTo(messageId);
        assertThat(result.sequence()).isEqualTo(17L);
        verifyNoInteractions(limiter, outbox, conversations);
    }

    @Test
    void readCursorNeverMovesBackwards() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        ChatConversation conversation = new ChatConversation(ticketId, "TK-1", "Printer", "IN_PROGRESS",
            actorId, UUID.randomUUID());
        conversation.recordMessage(10, "latest", Instant.now());
        ChatReadState state = new ChatReadState(conversation.getId(), actorId);
        state.advance(8);
        when(tickets.get(ticketId, "token")).thenReturn(context(ticketId, actorId, null, true));
        when(conversations.findByTicketId(ticketId)).thenReturn(Optional.of(conversation));
        when(reads.findByConversationIdAndUserId(conversation.getId(), actorId)).thenReturn(Optional.of(state));
        when(json.writeValueAsString(any())).thenReturn("{}");

        long result = service.markRead(actorId, "token", ticketId, 3, "read-1");

        assertThat(result).isEqualTo(8);
        assertThat(state.getLastReadSequence()).isEqualTo(8);
        verify(reads).save(state);
        verify(outbox).save(any());
    }

    @Test
    void loadsFirstInboxPageWithoutPassingANullTimestampToPostgres() {
        UUID actorId = UUID.randomUUID();
        when(conversations.searchInboxFirstPage(eq(actorId), eq(true), eq(""), eq("ALL"), eq(false), any(Pageable.class)))
            .thenReturn(List.of());
        when(tickets.resolve(anyCollection(), eq("token"))).thenReturn(List.of());

        var result = service.inbox(actorId, Set.of("ROLE_ADMIN"), "token", null, 20);

        assertThat(result.content()).isEmpty();
        verify(conversations).searchInboxFirstPage(eq(actorId), eq(true), eq(""), eq("ALL"), eq(false), any(Pageable.class));
        verify(conversations, never()).searchInboxBefore(any(), anyBoolean(), any(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void usesTimestampPredicateWhenLoadingANextInboxPage() {
        UUID actorId = UUID.randomUUID();
        Instant before = Instant.parse("2026-10-15T02:00:00Z");
        String cursor = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(before.toString().getBytes(StandardCharsets.UTF_8));
        when(conversations.searchInboxBefore(eq(actorId), eq(true), eq(before), eq(""), eq("ALL"), eq(false), any(Pageable.class)))
            .thenReturn(List.of());
        when(tickets.resolve(anyCollection(), eq("token"))).thenReturn(List.of());

        var result = service.inbox(actorId, Set.of("ROLE_MANAGER"), "token", cursor, 20);

        assertThat(result.content()).isEmpty();
        verify(conversations).searchInboxBefore(eq(actorId), eq(true), eq(before), eq(""), eq("ALL"), eq(false), any(Pageable.class));
        verify(conversations, never()).searchInboxFirstPage(any(), anyBoolean(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void usesTimestampAndIdForAStableNextPageCursor() {
        UUID actorId = UUID.randomUUID();
        UUID beforeId = UUID.randomUUID();
        Instant before = Instant.parse("2026-10-15T02:00:00Z");
        String cursor = Base64.getUrlEncoder().withoutPadding()
            .encodeToString((before + "|" + beforeId).getBytes(StandardCharsets.UTF_8));
        when(conversations.searchInboxAfterCursor(
            eq(actorId), eq(true), eq(before), eq(beforeId), eq(""), eq("ALL"), eq(false), any(Pageable.class)
        )).thenReturn(List.of());
        when(tickets.resolve(anyCollection(), eq("token"))).thenReturn(List.of());

        var result = service.inbox(actorId, Set.of("ROLE_ADMIN"), "token", cursor, 20);

        assertThat(result.content()).isEmpty();
        verify(conversations).searchInboxAfterCursor(
            eq(actorId), eq(true), eq(before), eq(beforeId), eq(""), eq("ALL"), eq(false), any(Pageable.class)
        );
    }

    private ChatTicketClient.ChatContext context(UUID ticketId, UUID requester, UUID assignee, boolean writable) {
        return new ChatTicketClient.ChatContext(ticketId, "TK-1", "Printer", "IN_PROGRESS",
            requester, assignee, writable);
    }
}
