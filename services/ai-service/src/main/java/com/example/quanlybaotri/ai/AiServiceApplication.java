package com.example.quanlybaotri.ai;

import com.example.quanlybaotri.ai.config.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
public class AiServiceApplication {
    public static void main(String[] args) { SpringApplication.run(AiServiceApplication.class, args); }
}
