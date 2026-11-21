package com.example.customerimport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CustomerImport — Spring Batch CSV import pipeline.
 *
 * <p>Spring Batch auto-configuration is active via {@code @SpringBootApplication}.
 * {@code @EnableBatchProcessing} is intentionally omitted: in Spring Boot 4 / Batch 6,
 * adding it overrides Boot's auto-configuration of {@code JobRepository},
 * {@code JobOperator}, and transaction management, which is not what we want here.
 * See ADR-001.
 */
@SpringBootApplication
public class CustomerImportApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerImportApplication.class, args);
    }
}
