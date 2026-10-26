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
    public static final String CHAT_QUEUE = "notification.chat-events";
    public static final String CHAT_DLQ = "notification.chat-events.dlq";
    public static final String CHAT_TICKET_QUEUE = "notification.chat-ticket-events";
    public static final String CHAT_TICKET_DLQ = "notification.chat-ticket-events.dlq";

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
    Queue chatQueue() {
        return QueueBuilder.durable(CHAT_QUEUE).deadLetterExchange("").deadLetterRoutingKey(CHAT_DLQ).build();
    }

    @Bean
    Queue chatDlq() { return QueueBuilder.durable(CHAT_DLQ).build(); }

    @Bean
    Queue chatTicketQueue() {
        return QueueBuilder.durable(CHAT_TICKET_QUEUE).deadLetterExchange("").deadLetterRoutingKey(CHAT_TICKET_DLQ).build();
    }

    @Bean
    Queue chatTicketDlq() { return QueueBuilder.durable(CHAT_TICKET_DLQ).build(); }

    @Bean
    Declarables bindings(
        TopicExchange domainEvents,
        @Qualifier("notificationQueue") Queue queue,
        @Qualifier("chatQueue") Queue chatQueue,
        @Qualifier("chatTicketQueue") Queue chatTicketQueue
    ) {
        return new Declarables(
            BindingBuilder.bind(queue).to(domainEvents).with("maintenance.ticket.changed"),
            BindingBuilder.bind(queue).to(domainEvents).with("maintenance.system-feedback.changed"),
            BindingBuilder.bind(queue).to(domainEvents).with("inventory.part.low-stock"),
            BindingBuilder.bind(chatQueue).to(domainEvents).with("notification.chat.#"),
            BindingBuilder.bind(chatTicketQueue).to(domainEvents).with("maintenance.ticket.changed")
        );
    }
}
