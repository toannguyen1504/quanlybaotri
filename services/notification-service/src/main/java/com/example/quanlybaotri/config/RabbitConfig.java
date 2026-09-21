package com.example.quanlybaotri.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "domain.events";
    public static final String QUEUE = "notification.events";
    public static final String DLQ = "notification.events.dlq";

    @Bean
    TopicExchange domainEvents() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange("").deadLetterRoutingKey(DLQ).build();
    }

    @Bean
    Queue notificationDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Declarables bindings(TopicExchange domainEvents, @Qualifier("notificationQueue") Queue queue) {
        return new Declarables(
            BindingBuilder.bind(queue).to(domainEvents).with("maintenance.ticket.changed"),
            BindingBuilder.bind(queue).to(domainEvents).with("inventory.part.low-stock")
        );
    }
}
