package com.mariosmant.fintech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * POC Fintech — Spring Boot entry point.
 *
 * <p>Scans all packages under {@code com.mariosmant.fintech} to wire
 * domain, application, and infrastructure layers together.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.mariosmant.fintech")
@EnableScheduling
public class FintechApplication {

    public static void main(String[] args) {
        SpringApplication.run(FintechApplication.class, args);
    }
}

