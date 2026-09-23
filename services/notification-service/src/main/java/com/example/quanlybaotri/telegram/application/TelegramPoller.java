package com.example.quanlybaotri.telegram.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TelegramPoller {

    private static final Logger log = LoggerFactory.getLogger(TelegramPoller.class);

    private final TelegramProperties properties;
    private final TelegramClient client;
    private final TelegramUpdateProcessor processor;
    private final TelegramUpdateCheckpoint checkpoint;
    private boolean initialized;
    private int consecutiveFailures;
    private Instant retryAt = Instant.EPOCH;

    public TelegramPoller(
        TelegramProperties properties,
        TelegramClient client,
        TelegramUpdateProcessor processor,
        TelegramUpdateCheckpoint checkpoint
    ) {
        this.properties = properties;
        this.client = client;
        this.processor = processor;
        this.checkpoint = checkpoint;
    }

    @Scheduled(
        fixedDelayString = "${app.telegram.poll-delay-ms:1000}",
        initialDelayString = "${app.telegram.poll-initial-delay-ms:5000}"
    )
    public void poll() {
        if (!properties.isConfigured()) return;
        if (Instant.now().isBefore(retryAt)) return;
        try {
            if (!initialized) {
                client.deleteWebhook();
                initialized = true;
            }
            for (TelegramClient.Update update : client.getUpdates(checkpoint.nextOffset())) {
                processor.process(update);
                checkpoint.advance(update.updateId());
            }
            consecutiveFailures = 0;
            retryAt = Instant.EPOCH;
        } catch (RuntimeException failure) {
            consecutiveFailures++;
            long delaySeconds = Math.min(60, 1L << Math.min(consecutiveFailures, 6));
            retryAt = Instant.now().plus(delaySeconds, ChronoUnit.SECONDS);
            log.warn(
                "Telegram long polling failed; retrying in {} seconds: {}",
                delaySeconds,
                safeMessage(failure)
            );
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
            ? failure.getClass().getSimpleName()
            : message.replaceAll("bot[0-9]+:[A-Za-z0-9_-]+", "bot[REDACTED]");
    }
}
