package com.totemena.elite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EliteApplication {
    public static void main(String[] args) {
        SpringApplication.run(EliteApplication.class, args);
    }
}
