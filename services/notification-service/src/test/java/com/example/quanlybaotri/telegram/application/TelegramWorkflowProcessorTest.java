package com.example.quanlybaotri.telegram.application;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.telegram.domain.*;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramWorkflowProcessorTest {
    private UUID userId;
    private TelegramConversationService conversations;
    private MaintenanceTicketClient maintenance;
    private InventoryWorkflowClient inventory;
    private TelegramClient client;
    private TelegramWorkflowProcessor processor;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        TelegramLinkRepository links = mock(TelegramLinkRepository.class);
        when(links.findByTelegramUserId(123L)).thenReturn(Optional.of(
            new TelegramLink(userId, 123L, 456L, "tech")
        ));
        conversations = mock(TelegramConversationService.class);
        maintenance = mock(MaintenanceTicketClient.class);
        inventory = mock(InventoryWorkflowClient.class);
        client = mock(TelegramClient.class);
        processor = new TelegramWorkflowProcessor(
            links, conversations, maintenance, inventory, client, new SimpleMeterRegistry()
        );
    }

    @Test
    void listsAssignedTicketsFromCommand() {
        UUID ticketId = UUID.randomUUID();
        when(maintenance.list(userId, 0)).thenReturn(new MaintenanceTicketClient.TicketPage(
            List.of(ticket(ticketId, "IN_PROGRESS")), 0, 1, 1
        ));

        processor.process(message(10L, "/phieu"));

        verify(client).sendMessage(eq(456L), contains("Phiếu bảo trì"), argThat(rows ->
            rows.stream().flatMap(List::stream)
                .anyMatch(button -> ("wf:open:" + ticketId).equals(button.callbackData()))
        ));
    }

    @Test
    void keepsStableCommandKeyWhenConfirmingWorkLog() {
        UUID ticketId = UUID.randomUUID();
        TelegramConversation conversation = new TelegramConversation(userId, Duration.ofHours(2));
        conversation.select(ticketId, Duration.ofHours(2));
        conversation.step(TelegramConversation.State.LOG_CONFIRM,
            "{\"content\":\"Kiểm tra nguồn\",\"minutes\":\"15\",\"commandKey\":\"telegram-command:stable\"}",
            Duration.ofHours(2));
        when(conversations.get(userId)).thenReturn(conversation);
        when(conversations.draft(conversation)).thenReturn(new LinkedHashMap<>(Map.of(
            "content", "Kiểm tra nguồn",
            "minutes", "15",
            "commandKey", "telegram-command:stable"
        )));
        when(maintenance.addWorkLog(ticketId, userId, "Kiểm tra nguồn", 15,
            "telegram-command:stable")).thenReturn(
                new MaintenanceTicketClient.WorkLogRef(UUID.randomUUID(), "Kiểm tra nguồn", 15)
            );

        processor.process(callback(11L, "wf:yes"));

        verify(maintenance).addWorkLog(ticketId, userId, "Kiểm tra nguồn", 15,
            "telegram-command:stable");
        verify(conversations).menu(userId);
    }

    @Test
    void rejectsWorkflowCallbackFromDifferentChat() {
        processor.process(new TelegramClient.Update(
            12L, "", 999L, "private", 123L, "tech", "cb", "wf:list:0", 1L
        ));

        verify(client).answerCallbackQuery("cb", "Tài khoản Telegram chưa được liên kết.", true);
        verifyNoInteractions(maintenance, inventory);
    }

    private TelegramClient.Update message(long updateId, String text) {
        return new TelegramClient.Update(updateId, text, 456L, "private", 123L, "tech");
    }

    private TelegramClient.Update callback(long updateId, String data) {
        return new TelegramClient.Update(updateId, "", 456L, "private", 123L, "tech",
            "callback-" + updateId, data, 1L);
    }

    private MaintenanceTicketClient.TicketRef ticket(UUID id, String status) {
        return new MaintenanceTicketClient.TicketRef(id, "BT-2026-000001", "TB-01", "Máy chủ",
            "Trung tâm", "Kiểm tra thiết bị", "Mô tả", "HIGH", status, "DRAFT",
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
