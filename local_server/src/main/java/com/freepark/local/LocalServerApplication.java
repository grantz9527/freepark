package com.freepark.local;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LocalServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalServerApplication.class, args);
    }
}
