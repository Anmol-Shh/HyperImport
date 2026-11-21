package com.example.customerimport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared Testcontainers configuration for all integration tests.
 *
 * <p>All integration tests import this class via {@code @Import(TestcontainersConfig.class)}.
 * The {@code @ServiceConnection} annotation instructs Boot's auto-configuration to read
 * the container's JDBC URL, username, and password and use them as the datasource —
 * no manual property override needed.
 *
 * <p>Image tag {@code postgres:16.16} is pinned here and must match {@code compose.yaml}.
 * If either changes, update both.
 *
 * <p>Exact class and package in Testcontainers 2.x:
 * <ul>
 *   <li>Package: {@code org.testcontainers.postgresql} (non-deprecated in 2.x)</li>
 *   <li>Class: {@code PostgreSQLContainer} (non-generic, extends {@code JdbcDatabaseContainer})</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16.16");
    }
}
