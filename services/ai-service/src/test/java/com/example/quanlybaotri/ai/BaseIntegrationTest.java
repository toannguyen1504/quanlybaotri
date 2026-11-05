package com.example.quanlybaotri.ai;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "app.ai.api-key=test-key",
    "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
@Import(AiIntegrationContainers.class)
public abstract class BaseIntegrationTest {}

@TestConfiguration(proxyBeanMethods = false)
class AiIntegrationContainers {

    @Bean
    @ServiceConnection
    RedisContainer redisContainer() {
        return new RedisContainer(DockerImageName.parse("redis:8-alpine"));
    }
}
