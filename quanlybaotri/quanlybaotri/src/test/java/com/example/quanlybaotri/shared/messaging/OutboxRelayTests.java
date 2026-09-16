package com.example.quanlybaotri.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OutboxRelayTests {
    private OutboxRepository repository;
    private RabbitTemplate rabbit;
    private OutboxEvent event;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        rabbit = mock(RabbitTemplate.class);
        event = event("ticket.submitted");
        relay = new OutboxRelay(repository, rabbit, Duration.ofMillis(25));
        when(repository.findTop50ByPublishedAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(event));
    }

    @Test
    void marksEventPublishedOnlyAfterBrokerAck() {
        completePublishWith(new CorrelationData.Confirm(true, null), null);

        relay.publish();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void keepsEventPendingAfterBrokerNack() {
        completePublishWith(new CorrelationData.Confirm(false, "broker rejected message"), null);

        relay.publish();

        assertFailedWith("broker rejected message");
    }

    @Test
    void keepsEventPendingWhenMessageIsReturnedAsUnroutable() {
        ReturnedMessage returned = new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE",
                RabbitConfig.EXCHANGE, "unknown.route");
        completePublishWith(new CorrelationData.Confirm(true, null), returned);

        relay.publish();

        assertFailedWith("NO_ROUTE");
    }

    @Test
    void keepsEventPendingWhenConfirmTimesOut() {
        relay.publish();

        assertFailedWith("TimeoutException");
    }

    @Test
    void keepsEventPendingWhenSendThrows() {
        doThrow(new IllegalStateException("connection refused")).when(rabbit).convertAndSend(
                eq(RabbitConfig.EXCHANGE), eq(event.getRoutingKey()), eq(event.getPayloadJson()),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        relay.publish();

        assertFailedWith("connection refused");
    }

    private void completePublishWith(CorrelationData.Confirm confirm, ReturnedMessage returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            if (returned != null) {
                correlation.setReturned(returned);
            }
            correlation.getFuture().complete(confirm);
            return null;
        }).when(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(event.getRoutingKey()),
                eq(event.getPayloadJson()), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    private void assertFailedWith(String message) {
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains(message);
    }

    private OutboxEvent event(String routingKey) {
        return new OutboxEvent(UUID.randomUUID(), "TICKET", UUID.randomUUID(), "SUBMITTED", routingKey, "{}");
    }
}
