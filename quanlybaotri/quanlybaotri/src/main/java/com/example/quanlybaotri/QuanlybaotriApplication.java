package com.example.quanlybaotri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuanlybaotriApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuanlybaotriApplication.class, args);
    }

}
