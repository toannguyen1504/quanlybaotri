package com.example.quanlybaotri;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "app.service-clients.identity-secret=test-identity-service-secret",
    "app.service-clients.asset-secret=test-asset-service-secret",
    "app.service-clients.maintenance-secret=test-maintenance-service-secret",
    "app.service-clients.payment-secret=test-payment-service-secret",
    "app.service-clients.inventory-secret=test-inventory-service-secret",
    "app.service-clients.notification-secret=test-notification-service-secret",
    "app.jwt.key-directory=target/test-jwt-keys",
    "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
@Import(IdentityIntegrationContainers.class)
public abstract class BaseIntegrationTest {}

@TestConfiguration(proxyBeanMethods = false)
class IdentityIntegrationContainers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection
    RedisContainer redisContainer() {
        return new RedisContainer(DockerImageName.parse("redis:8-alpine"));
    }
}
