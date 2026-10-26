package com.example.quanlybaotri.shared.messaging;

import com.example.quanlybaotri.config.RabbitConfig;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxRepository repository;
    private final RabbitTemplate rabbit;
    private final Duration timeout;

    public OutboxRelay(OutboxRepository repository, RabbitTemplate rabbit,
        @Value("${app.outbox.confirm-timeout:5s}") Duration timeout) {
        this.repository = repository; this.rabbit = rabbit; this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${app.outbox.delay-ms:1000}", initialDelayString = "${app.outbox.initial-delay-ms:1000}")
    @Transactional
    public void publish() {
        for (OutboxEvent event : repository.findTop50ByPublishedAtIsNullOrderByOccurredAtAsc()) {
            try {
                CorrelationData correlation = new CorrelationData(event.getId().toString());
                rabbit.convertAndSend(RabbitConfig.EXCHANGE, event.getRoutingKey(), event.getPayloadJson(), correlation);
                var confirm = correlation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!confirm.ack() || correlation.getReturned() != null) throw new IllegalStateException("RabbitMQ rejected chat event");
                event.published();
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                event.failed(error.getMessage());
                log.warn("Could not publish chat outbox event {}", event.getId(), error);
                break;
            }
        }
    }
}
