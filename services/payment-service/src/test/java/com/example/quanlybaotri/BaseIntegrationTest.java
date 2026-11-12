package com.example.quanlybaotri;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "app.service-client.secret=test-payment-service-secret",
    "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
@Import(PaymentIntegrationContainers.class)
public abstract class BaseIntegrationTest {}

@TestConfiguration(proxyBeanMethods = false)
class PaymentIntegrationContainers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }
}
