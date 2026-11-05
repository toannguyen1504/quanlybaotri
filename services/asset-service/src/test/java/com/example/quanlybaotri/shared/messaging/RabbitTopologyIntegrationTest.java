package com.example.quanlybaotri.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

class RabbitTopologyIntegrationTest extends BaseIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired @Qualifier("assetQueue") Queue queue;

    @Test
    void should_declare_exchange_queue_and_dead_letter_route() {
        Boolean declared = rabbit.execute(channel -> {
            channel.exchangeDeclarePassive(RabbitConfig.EXCHANGE);
            channel.queueDeclarePassive(RabbitConfig.QUEUE);
            channel.queueDeclarePassive(RabbitConfig.DLQ);
            return true;
        });

        assertThat(declared).isTrue();
        assertThat(queue.getArguments())
            .containsEntry("x-dead-letter-exchange", "")
            .containsEntry("x-dead-letter-routing-key", RabbitConfig.DLQ);
    }
}
