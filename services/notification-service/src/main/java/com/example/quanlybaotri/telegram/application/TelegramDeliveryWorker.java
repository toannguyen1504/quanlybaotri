package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.telegram.domain.TelegramDelivery;
import com.example.quanlybaotri.telegram.domain.TelegramLink;
import com.example.quanlybaotri.telegram.persistence.TelegramDeliveryRepository;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TelegramDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(TelegramDeliveryWorker.class);
    private static final int MAX_ATTEMPTS = 5;

    private final TelegramProperties properties;
    private final TelegramDeliveryRepository deliveries;
    private final TelegramLinkRepository links;
    private final IdentityClient identities;
    private final TelegramClient client;

    public TelegramDeliveryWorker(
        TelegramProperties properties,
        TelegramDeliveryRepository deliveries,
        TelegramLinkRepository links,
        IdentityClient identities,
        TelegramClient client
    ) {
        this.properties = properties;
        this.deliveries = deliveries;
        this.links = links;
        this.identities = identities;
        this.client = client;
    }

    @Scheduled(
        fixedDelayString = "${app.telegram.delivery-delay-ms:1000}",
        initialDelayString = "${app.telegram.delivery-initial-delay-ms:7000}"
    )
    @Transactional
    public void deliver() {
        if (!properties.isConfigured()) return;
        for (TelegramDelivery delivery : deliveries.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            TelegramDelivery.Status.PENDING,
            Instant.now()
        )) {
            deliverOne(delivery);
        }
    }

    private void deliverOne(TelegramDelivery delivery) {
        TelegramLink link = links.findByUserIdAndActiveTrue(delivery.getUserId()).orElse(null);
        if (link == null) {
            delivery.cancel("Telegram link is not active");
            return;
        }
        try {
            IdentityClient.UserRef user = identities.get(delivery.getUserId());
            if (user == null || !user.isEnabledTechnician()) {
                link.disable();
                delivery.cancel("User is no longer an enabled technician");
                return;
            }
            String buttonUrl = ticketButtonUrl(delivery);
            long messageId = client.sendMessage(
                link.getTelegramChatId(),
                delivery.getMessageText(),
                buttonUrl
            );
            delivery.sent(messageId);
        } catch (TelegramApiException failure) {
            if (failure.getErrorCode() == 403) {
                link.disable();
                delivery.fail("Telegram bot was blocked or cannot access the chat");
                return;
            }
            if (
                failure.getErrorCode() >= 400 &&
                failure.getErrorCode() < 500 &&
                failure.getErrorCode() != 429
            ) {
                delivery.fail(failure.getMessage());
                log.warn(
                    "Telegram delivery {} failed with a non-retryable error: {}",
                    delivery.getId(),
                    safeMessage(failure)
                );
                return;
            }
            retryOrFail(delivery, failure, failure.getRetryAfterSeconds());
        } catch (RuntimeException failure) {
            retryOrFail(delivery, failure, null);
        }
    }

    private String ticketButtonUrl(TelegramDelivery delivery) {
        if (delivery.getReferenceId() == null || properties.getAppPublicUrl().isBlank()) return null;
        try {
            URI base = URI.create(properties.getAppPublicUrl());
            String host = base.getHost();
            if (
                host == null ||
                "localhost".equalsIgnoreCase(host) ||
                "127.0.0.1".equals(host) ||
                "::1".equals(host)
            ) return null;
            String scheme = base.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            return properties.getAppPublicUrl() + "/tickets/" + delivery.getReferenceId();
        } catch (IllegalArgumentException invalidUrl) {
            return null;
        }
    }

    private void retryOrFail(
        TelegramDelivery delivery,
        RuntimeException failure,
        Integer retryAfterSeconds
    ) {
        String error = safeMessage(failure);
        if (delivery.getAttempts() + 1 >= MAX_ATTEMPTS) {
            delivery.fail(error);
            log.warn("Telegram delivery {} failed permanently: {}", delivery.getId(), error);
            return;
        }
        long delay = retryAfterSeconds == null
            ? Math.min(300, 1L << Math.min(delivery.getAttempts() + 1, 8))
            : Math.max(1, retryAfterSeconds);
        delivery.retry(error, Instant.now().plus(delay, ChronoUnit.SECONDS));
        log.warn("Telegram delivery {} will retry in {} seconds: {}", delivery.getId(), delay, error);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
            ? failure.getClass().getSimpleName()
            : message.replaceAll("bot[0-9]+:[A-Za-z0-9_-]+", "bot[REDACTED]");
    }
}
