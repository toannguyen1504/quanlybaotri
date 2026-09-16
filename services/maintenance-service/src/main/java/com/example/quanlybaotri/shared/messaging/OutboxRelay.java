package com.example.quanlybaotri.shared.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.ReturnedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repo;
    private final RabbitTemplate rabbit;
    private final Duration confirmTimeout;

    public OutboxRelay(OutboxRepository r, RabbitTemplate rabbit,
            @Value("${app.outbox.confirm-timeout:5s}") Duration confirmTimeout) {
        repo = r;
        this.rabbit = rabbit;
        this.confirmTimeout = confirmTimeout;
    }

    @Scheduled(fixedDelayString = "${app.outbox.delay-ms:3000}",
            initialDelayString = "${app.outbox.initial-delay-ms:0}")
    @Transactional
    public void publish() {
        for (OutboxEvent e : repo.findTop50ByPublishedAtIsNullOrderByOccurredAtAsc()) {
            try {
                CorrelationData correlation = new CorrelationData(e.getId().toString());
                rabbit.convertAndSend(RabbitConfig.EXCHANGE, e.getRoutingKey(), e.getPayloadJson(), m -> {
                    m.getMessageProperties().setHeader("eventId", e.getId().toString());
                    return m;
                }, correlation);

                CorrelationData.Confirm confirm = correlation.getFuture()
                        .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
                ReturnedMessage returned = correlation.getReturned();
                if (returned != null) {
                    throw new IllegalStateException("RabbitMQ returned message: " + returned.getReplyText()
                            + " (exchange=" + returned.getExchange() + ", routingKey=" + returned.getRoutingKey()
                            + ")");
                }
                if (!confirm.ack()) {
                    throw new IllegalStateException("RabbitMQ rejected publish: "
                            + (confirm.reason() == null ? "NACK" : confirm.reason()));
                }
                e.published();
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                String error = errorMessage(ex);
                e.failed(error);
                log.warn("RabbitMQ publish failed for outbox event {} with routing key {}: {}", e.getId(),
                        e.getRoutingKey(), error);
                break;
            }
        }
    }

    private String errorMessage(Exception error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
