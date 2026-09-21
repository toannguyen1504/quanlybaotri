package com.example.quanlybaotri.shared.messaging;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "domain.events";
    public static final String QUEUE = "asset.domain-events";
    public static final String DLQ = "asset.domain-events.dlq";

    @Bean
    TopicExchange domainEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue assetQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange("").deadLetterRoutingKey(DLQ).build();
    }

    @Bean
    Queue assetDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Declarables identityBinding(
        TopicExchange domainEventsExchange,
        @Qualifier("assetQueue") Queue queue
    ) {
        return new Declarables(
            BindingBuilder.bind(queue).to(domainEventsExchange).with("maintenance.ticket.changed")
        );
    }
}
