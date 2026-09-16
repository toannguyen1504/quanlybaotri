package com.example.quanlybaotri.shared.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE="domain.events";
    public static final String QUEUE="maintenance.domain-events";
    public static final String DLQ="maintenance.domain-events.dlq";
    @Bean TopicExchange domainEventsExchange(){return new TopicExchange(EXCHANGE,true,false);}
    @Bean Queue maintenanceQueue(){return QueueBuilder.durable(QUEUE).deadLetterExchange("").deadLetterRoutingKey(DLQ).build();}
    @Bean Queue maintenanceDlq(){return QueueBuilder.durable(DLQ).build();}
    @Bean Declarables maintenanceBindings(TopicExchange domainEventsExchange,
            @Qualifier("maintenanceQueue") Queue maintenanceQueue){
        return new Declarables(BindingBuilder.bind(maintenanceQueue).to(domainEventsExchange).with("inventory.part.used"));
    }
}
