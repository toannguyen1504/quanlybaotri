package com.example.quanlybaotri.telegram.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.telegram.domain.TelegramDelivery;
import com.example.quanlybaotri.telegram.domain.TelegramLink;
import com.example.quanlybaotri.telegram.persistence.TelegramDeliveryRepository;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramDeliveryWorkerTest {

    private TelegramProperties properties;
    private TelegramDeliveryRepository deliveries;
    private TelegramLinkRepository links;
    private IdentityClient identities;
    private TelegramClient client;
    private TelegramDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        properties = new TelegramProperties();
        properties.setEnabled(true);
        properties.setBotToken("test-token");
        deliveries = mock(TelegramDeliveryRepository.class);
        links = mock(TelegramLinkRepository.class);
        identities = mock(IdentityClient.class);
        client = mock(TelegramClient.class);
        worker = new TelegramDeliveryWorker(properties, deliveries, links, identities, client);
    }

    @Test
    void honorsTelegramRetryAfter() {
        UUID userId = UUID.randomUUID();
        TelegramDelivery delivery = delivery(userId);
        TelegramLink link = link(userId);
        arrange(delivery, link);
        when(client.sendMessage(anyLong(), anyString(), nullable(String.class))).thenThrow(
            new TelegramApiException(429, "Too Many Requests", 30)
        );
        Instant before = Instant.now();

        worker.deliver();

        assertThat(delivery.getStatus()).isEqualTo(TelegramDelivery.Status.PENDING);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isAfterOrEqualTo(before.plusSeconds(30));
    }

    @Test
    void disablesLinkWhenBotIsBlocked() {
        UUID userId = UUID.randomUUID();
        TelegramDelivery delivery = delivery(userId);
        TelegramLink link = link(userId);
        arrange(delivery, link);
        when(client.sendMessage(anyLong(), anyString(), nullable(String.class))).thenThrow(
            new TelegramApiException(403, "Forbidden", null)
        );

        worker.deliver();

        assertThat(link.isActive()).isFalse();
        assertThat(delivery.getStatus()).isEqualTo(TelegramDelivery.Status.FAILED);
    }

    @Test
    void omitsTicketButtonForLocalhostAndStillSendsMessage() {
        UUID userId = UUID.randomUUID();
        TelegramDelivery delivery = delivery(userId);
        arrange(delivery, link(userId));
        when(client.sendMessage(anyLong(), anyString(), isNull())).thenReturn(99L);

        worker.deliver();

        assertThat(delivery.getStatus()).isEqualTo(TelegramDelivery.Status.SENT);
        verify(client).sendMessage(eq(123L), eq("Thông báo phiếu"), isNull());
    }

    private void arrange(TelegramDelivery delivery, TelegramLink link) {
        when(
            deliveries.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(TelegramDelivery.Status.PENDING),
                any(Instant.class)
            )
        )
            .thenReturn(List.of(delivery));
        when(links.findByUserIdAndActiveTrue(delivery.getUserId())).thenReturn(Optional.of(link));
        when(identities.get(delivery.getUserId())).thenReturn(
            new IdentityClient.UserRef(
                delivery.getUserId(),
                "tech",
                "Kỹ thuật viên",
                "tech@example.test",
                null,
                true,
                Set.of("TECHNICIAN")
            )
        );
    }

    private TelegramDelivery delivery(UUID userId) {
        return new TelegramDelivery(
            UUID.randomUUID(),
            userId,
            "Thông báo phiếu",
            UUID.randomUUID()
        );
    }

    private TelegramLink link(UUID userId) {
        return new TelegramLink(userId, 123L, 123L, "tech");
    }
}
