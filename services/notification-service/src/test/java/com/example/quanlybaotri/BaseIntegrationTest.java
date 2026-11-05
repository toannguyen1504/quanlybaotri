package com.example.quanlybaotri;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "app.chat.enabled=false",
    "spring.task.scheduling.enabled=false",
    "spring.rabbitmq.listener.simple.retry.max-attempts=3",
    "spring.rabbitmq.listener.simple.retry.initial-interval=10ms",
    "spring.rabbitmq.listener.simple.retry.multiplier=1",
    "spring.rabbitmq.listener.simple.retry.max-interval=10ms"
})
@AutoConfigureMockMvc
@Import(NotificationIntegrationContainers.class)
public abstract class BaseIntegrationTest {}

@TestConfiguration(proxyBeanMethods = false)
class NotificationIntegrationContainers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMQContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));
    }

    @Bean
    @ServiceConnection
    RedisContainer redisContainer() {
        return new RedisContainer(DockerImageName.parse("redis:8-alpine"));
    }
}
