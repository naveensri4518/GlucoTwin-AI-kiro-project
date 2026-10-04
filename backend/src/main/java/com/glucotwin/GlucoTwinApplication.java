package com.glucotwin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GlucoTwinApplication {
    public static void main(String[] args) {
        SpringApplication.run(GlucoTwinApplication.class, args);
    }
}
