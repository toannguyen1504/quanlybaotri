package com.example.quanlybaotri.shared.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "maintenance.events";
    public static final String NOTIFICATION_QUEUE = "maintenance.notification";
    public static final String DLQ = "maintenance.notification.dlq";

    @Bean
    TopicExchange maintenanceExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).deadLetterExchange("").deadLetterRoutingKey(DLQ).build();
    }

    @Bean
    Queue notificationDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Declarables notificationBindings(TopicExchange maintenanceExchange, Queue notificationQueue) {
        return new Declarables(BindingBuilder.bind(notificationQueue).to(maintenanceExchange).with("ticket.#"),
                BindingBuilder.bind(notificationQueue).to(maintenanceExchange).with("sla.#"),
                BindingBuilder.bind(notificationQueue).to(maintenanceExchange).with("part.#"));
    }
}
