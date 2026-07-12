package com.fundpilot.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableFeignClients
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class FundPilotBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundPilotBackendApplication.class, args);
    }
}
