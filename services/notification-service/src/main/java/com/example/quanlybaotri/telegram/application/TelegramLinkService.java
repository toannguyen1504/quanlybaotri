package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.telegram.domain.TelegramLink;
import com.example.quanlybaotri.telegram.domain.TelegramLinkToken;
import com.example.quanlybaotri.telegram.domain.TelegramDelivery;
import com.example.quanlybaotri.telegram.persistence.TelegramDeliveryRepository;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkRepository;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TelegramProperties properties;
    private final TelegramLinkRepository links;
    private final TelegramLinkTokenRepository tokens;
    private final TelegramDeliveryRepository deliveries;

    public TelegramLinkService(
        TelegramProperties properties,
        TelegramLinkRepository links,
        TelegramLinkTokenRepository tokens,
        TelegramDeliveryRepository deliveries
    ) {
        this.properties = properties;
        this.links = links;
        this.tokens = tokens;
        this.deliveries = deliveries;
    }

    @Transactional(readOnly = true)
    public Status status(UUID userId) {
        TelegramLink link = links.findByUserIdAndActiveTrue(userId).orElse(null);
        return new Status(
            properties.isConfigured(),
            link != null,
            link == null ? null : link.getTelegramUsername(),
            link == null ? null : link.getLinkedAt()
        );
    }

    @Transactional
    public LinkRequest createLink(UUID userId) {
        requireConfigured();
        tokens.deleteByUserId(userId);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(properties.getLinkTtl());
        tokens.save(new TelegramLinkToken(hash(raw), userId, expiresAt));
        String url = "https://t.me/" + properties.getBotUsername() + "?start=" + raw;
        return new LinkRequest(url, expiresAt);
    }

    @Transactional
    public void disconnect(UUID userId) {
        tokens.deleteByUserId(userId);
        links.deleteById(userId);
        deliveries
            .findByUserIdAndStatus(userId, TelegramDelivery.Status.PENDING)
            .forEach(delivery -> delivery.cancel("Telegram link was disconnected"));
    }

    @Transactional
    public ConsumeResult consume(
        String rawToken,
        long telegramUserId,
        long telegramChatId,
        String telegramUsername
    ) {
        TelegramLinkToken token = tokens.findLockedByTokenHash(hash(rawToken)).orElse(null);
        if (token == null) return ConsumeResult.INVALID;
        tokens.delete(token);
        if (!token.getExpiresAt().isAfter(Instant.now())) return ConsumeResult.EXPIRED;

        TelegramLink other = links.findByTelegramUserId(telegramUserId).orElse(null);
        if (other != null && !other.getUserId().equals(token.getUserId())) return ConsumeResult.CONFLICT;

        TelegramLink link = links.findById(token.getUserId()).orElse(null);
        if (link == null) {
            links.save(
                new TelegramLink(
                    token.getUserId(),
                    telegramUserId,
                    telegramChatId,
                    telegramUsername
                )
            );
        } else {
            link.reconnect(telegramUserId, telegramChatId, telegramUsername);
        }
        return ConsumeResult.LINKED;
    }

    @Scheduled(fixedDelayString = "${app.telegram.token-cleanup-ms:3600000}")
    @Transactional
    public void deleteExpiredTokens() {
        tokens.deleteByExpiresAtBefore(Instant.now());
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "TELEGRAM_UNAVAILABLE",
            "Kênh Telegram chưa được cấu hình"
        );
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Status(
        boolean available,
        boolean linked,
        String telegramUsername,
        Instant linkedAt
    ) {}

    public record LinkRequest(String linkUrl, Instant expiresAt) {}

    public enum ConsumeResult {
        LINKED,
        INVALID,
        EXPIRED,
        CONFLICT,
    }
}
