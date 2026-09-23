package com.example.quanlybaotri.telegram.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.telegram.domain.TelegramLinkToken;
import com.example.quanlybaotri.telegram.domain.TelegramLink;
import com.example.quanlybaotri.telegram.persistence.TelegramDeliveryRepository;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkRepository;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TelegramLinkServiceTest {

    private TelegramProperties properties;
    private TelegramLinkRepository links;
    private TelegramLinkTokenRepository tokens;
    private TelegramDeliveryRepository deliveries;
    private TelegramLinkService service;

    @BeforeEach
    void setUp() {
        properties = new TelegramProperties();
        properties.setEnabled(true);
        properties.setBotToken("test-token");
        properties.setBotUsername("technician001_bot");
        properties.setLinkTtl(Duration.ofMinutes(10));
        links = mock(TelegramLinkRepository.class);
        tokens = mock(TelegramLinkTokenRepository.class);
        deliveries = mock(TelegramDeliveryRepository.class);
        service = new TelegramLinkService(properties, links, tokens, deliveries);
    }

    @Test
    void createsShortSingleUseDeepLinkAndStoresOnlyItsHash() {
        UUID userId = UUID.randomUUID();

        TelegramLinkService.LinkRequest request = service.createLink(userId);

        assertThat(request.linkUrl()).startsWith(
            "https://t.me/technician001_bot?start="
        );
        String rawToken = request.linkUrl().substring(request.linkUrl().indexOf("?start=") + 7);
        assertThat(rawToken).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(request.expiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(9)));
        verify(tokens).deleteByUserId(userId);
        ArgumentCaptor<TelegramLinkToken> saved = ArgumentCaptor.forClass(
            TelegramLinkToken.class
        );
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
            .isEqualTo(TelegramLinkService.hash(rawToken))
            .doesNotContain(rawToken);
    }

    @Test
    void consumesTokenOnlyOnceAndCreatesLink() {
        UUID userId = UUID.randomUUID();
        String rawToken = "valid_token";
        TelegramLinkToken token = new TelegramLinkToken(
            TelegramLinkService.hash(rawToken),
            userId,
            Instant.now().plusSeconds(60)
        );
        when(tokens.findLockedByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        when(links.findByTelegramUserId(123L)).thenReturn(Optional.empty());
        when(links.findById(userId)).thenReturn(Optional.empty());

        TelegramLinkService.ConsumeResult result = service.consume(
            rawToken,
            123L,
            123L,
            "technician"
        );

        assertThat(result).isEqualTo(TelegramLinkService.ConsumeResult.LINKED);
        verify(tokens).delete(token);
        verify(links).save(any());
    }

    @Test
    void rejectsExpiredTokenWithoutCreatingLink() {
        UUID userId = UUID.randomUUID();
        String rawToken = "expired_token";
        TelegramLinkToken token = new TelegramLinkToken(
            TelegramLinkService.hash(rawToken),
            userId,
            Instant.now().minusSeconds(1)
        );
        when(tokens.findLockedByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));

        TelegramLinkService.ConsumeResult result = service.consume(
            rawToken,
            123L,
            123L,
            null
        );

        assertThat(result).isEqualTo(TelegramLinkService.ConsumeResult.EXPIRED);
        verify(tokens).delete(token);
        verifyNoInteractions(links);
    }

    @Test
    void preventsTelegramAccountFromBeingLinkedToAnotherUser() {
        UUID userId = UUID.randomUUID();
        String rawToken = "conflicting_token";
        TelegramLinkToken token = new TelegramLinkToken(
            TelegramLinkService.hash(rawToken),
            userId,
            Instant.now().plusSeconds(60)
        );
        when(tokens.findLockedByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        when(links.findByTelegramUserId(123L)).thenReturn(
            Optional.of(new TelegramLink(UUID.randomUUID(), 123L, 123L, "other"))
        );

        TelegramLinkService.ConsumeResult result = service.consume(
            rawToken,
            123L,
            123L,
            "technician"
        );

        assertThat(result).isEqualTo(TelegramLinkService.ConsumeResult.CONFLICT);
        verify(tokens).delete(token);
        verify(links, never()).save(any());
    }
}
