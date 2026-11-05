package com.example.quanlybaotri.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.quanlybaotri.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import java.time.Duration;

class RabbitTopologyIntegrationTest extends BaseIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired @Qualifier("notificationQueue") Queue notificationQueue;
    @Autowired @Qualifier("chatQueue") Queue chatQueue;
    @Autowired @Qualifier("chatTicketQueue") Queue chatTicketQueue;

    @Test
    void should_declare_all_queues_with_dead_letter_routes() {
        Boolean declared = rabbit.execute(channel -> {
            channel.exchangeDeclarePassive(RabbitConfig.EXCHANGE);
            for (String name : new String[] {
                RabbitConfig.QUEUE,
                RabbitConfig.DLQ,
                RabbitConfig.CHAT_QUEUE,
                RabbitConfig.CHAT_DLQ,
                RabbitConfig.CHAT_TICKET_QUEUE,
                RabbitConfig.CHAT_TICKET_DLQ
            }) channel.queueDeclarePassive(name);
            return true;
        });

        assertThat(declared).isTrue();
        assertDeadLetter(notificationQueue, RabbitConfig.DLQ);
        assertDeadLetter(chatQueue, RabbitConfig.CHAT_DLQ);
        assertDeadLetter(chatTicketQueue, RabbitConfig.CHAT_TICKET_DLQ);
    }

    @Test
    void should_retry_poison_event_and_dead_letter_it() {
        rabbit.execute(channel -> {
            channel.queuePurge(RabbitConfig.DLQ);
            return null;
        });

        rabbit.convertAndSend(
            RabbitConfig.EXCHANGE,
            "maintenance.ticket.changed",
            "{\"schemaVersion\":99,\"eventId\":\"00000000-0000-0000-0000-000000000001\"}"
        );

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(rabbit.receive(RabbitConfig.DLQ)).isNotNull()
        );
    }

    private void assertDeadLetter(Queue queue, String routingKey) {
        assertThat(queue.getArguments())
            .containsEntry("x-dead-letter-exchange", "")
            .containsEntry("x-dead-letter-routing-key", routingKey);
    }
}
