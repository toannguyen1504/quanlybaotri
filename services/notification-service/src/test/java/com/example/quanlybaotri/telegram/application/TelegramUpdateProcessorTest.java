package com.example.quanlybaotri.telegram.application;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.telegram.domain.TelegramDelivery;
import com.example.quanlybaotri.telegram.domain.TelegramLink;
import com.example.quanlybaotri.telegram.persistence.TelegramDeliveryRepository;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramUpdateProcessorTest {

    private TelegramLinkService links;
    private TelegramLinkRepository linkRepository;
    private TelegramDeliveryRepository deliveries;
    private MaintenanceTicketClient maintenance;
    private TelegramClient client;
    private TelegramUpdateProcessor processor;

    @BeforeEach
    void setUp() {
        links = mock(TelegramLinkService.class);
        linkRepository = mock(TelegramLinkRepository.class);
        deliveries = mock(TelegramDeliveryRepository.class);
        maintenance = mock(MaintenanceTicketClient.class);
        client = mock(TelegramClient.class);
        TelegramProperties properties = new TelegramProperties();
        properties.setAppPublicUrl("https://maintenance.example.test");
        processor = new TelegramUpdateProcessor(
            links,
            linkRepository,
            deliveries,
            maintenance,
            new TelegramKeyboardFactory(properties),
            client
        );
    }

    @Test
    void ignoresStartCommandsFromGroupChats() {
        processor.process(
            new TelegramClient.Update(1L, "/start valid_token", -100L, "group", 123L, "tech")
        );

        verifyNoInteractions(links, linkRepository, deliveries, maintenance, client);
    }

    @Test
    void startsAssignedTicketAndRemovesActionButton() {
        UUID userId = UUID.randomUUID();
        TelegramDelivery delivery = actionableDelivery(userId);
        TelegramLink link = new TelegramLink(userId, 123L, 456L, "tech");
        when(deliveries.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(linkRepository.findByTelegramUserId(123L)).thenReturn(Optional.of(link));
        when(maintenance.start(delivery.getReferenceId(), userId)).thenReturn(
            new MaintenanceTicketClient.StartResult(
                MaintenanceTicketClient.Outcome.STARTED,
                "IN_PROGRESS"
            )
        );

        processor.process(callback(delivery, 123L, 456L));

        verify(maintenance).start(delivery.getReferenceId(), userId);
        verify(client).editMessage(
            eq(456L),
            eq(77L),
            contains("Đã tiếp nhận"),
            argThat(rows -> rows.size() == 1 && rows.get(0).get(0).url() != null)
        );
        verify(client).answerCallbackQuery("callback-1", "Đã tiếp nhận và bắt đầu xử lý phiếu.", false);
    }

    @Test
    void rejectsCallbackFromAnotherTelegramUser() {
        UUID userId = UUID.randomUUID();
        TelegramDelivery delivery = actionableDelivery(userId);
        TelegramLink other = new TelegramLink(UUID.randomUUID(), 999L, 456L, "other");
        when(deliveries.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(linkRepository.findByTelegramUserId(999L)).thenReturn(Optional.of(other));

        processor.process(callback(delivery, 999L, 456L));

        verifyNoInteractions(maintenance);
        verify(client).answerCallbackQuery(
            "callback-1",
            "Bạn không có quyền thực hiện thao tác này.",
            true
        );
        verify(client, never()).editMessage(anyLong(), anyLong(), anyString(), anyList());
    }

    @Test
    void keepsActionButtonWhenMaintenanceIsTemporarilyUnavailable() {
        UUID userId = UUID.randomUUID();
        TelegramDelivery delivery = actionableDelivery(userId);
        TelegramLink link = new TelegramLink(userId, 123L, 456L, "tech");
        when(deliveries.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(linkRepository.findByTelegramUserId(123L)).thenReturn(Optional.of(link));
        when(maintenance.start(delivery.getReferenceId(), userId)).thenThrow(
            new IllegalStateException("temporarily unavailable")
        );

        processor.process(callback(delivery, 123L, 456L));

        verify(client, never()).editMessage(anyLong(), anyLong(), anyString(), anyList());
        verify(client).answerCallbackQuery(
            "callback-1",
            "Hệ thống đang tạm thời không xử lý được. Vui lòng thử lại.",
            true
        );
    }

    private TelegramDelivery actionableDelivery(UUID userId) {
        TelegramDelivery delivery = new TelegramDelivery(
            UUID.randomUUID(),
            userId,
            "Thông báo phiếu",
            UUID.randomUUID(),
            TelegramDelivery.Action.START_ASSIGNED_TICKET
        );
        delivery.sent(77L);
        return delivery;
    }

    private TelegramClient.Update callback(
        TelegramDelivery delivery,
        long telegramUserId,
        long chatId
    ) {
        return new TelegramClient.Update(
            2L,
            "Thông báo phiếu",
            chatId,
            "private",
            telegramUserId,
            "tech",
            "callback-1",
            "start:" + delivery.getId(),
            77L
        );
    }
}
