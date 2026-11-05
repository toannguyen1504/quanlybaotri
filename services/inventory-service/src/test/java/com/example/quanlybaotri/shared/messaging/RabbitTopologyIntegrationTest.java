package com.example.quanlybaotri.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

class RabbitTopologyIntegrationTest extends BaseIntegrationTest {

    @Autowired RabbitTemplate rabbit;

    @Test
    void should_declare_domain_event_exchange_for_outbox_publication() {
        Boolean declared = rabbit.execute(channel -> {
            channel.exchangeDeclarePassive(RabbitConfig.EXCHANGE);
            return true;
        });

        assertThat(declared).isTrue();
    }
}
