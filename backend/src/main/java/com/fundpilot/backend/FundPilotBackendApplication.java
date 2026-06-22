package com.fundpilot.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableFeignClients
@SpringBootApplication
@EnableJpaAuditing
public class FundPilotBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundPilotBackendApplication.class, args);
    }
}
