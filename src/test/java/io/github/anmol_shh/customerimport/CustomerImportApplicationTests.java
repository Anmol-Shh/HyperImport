package io.github.anmol_shh.customerimport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR 1 smoke test — verifies the application context loads and Actuator health is UP.
 *
 * <p>Uses Testcontainers (via {@link TestcontainersConfig}) so this passes on a clean
 * machine with Docker running but no Compose stack up. Docker Compose support is
 * skipped during tests by default; Testcontainers fills that role for CI and local
 * test runs.
 *
 * <p>No Flyway migrations exist yet (PR 2), so there is no schema assertion here.
 * The datasource must be reachable and the context must load — that's the full scope
 * of PR 1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class CustomerImportApplicationTests {

    @LocalServerPort
    int port;

    @Test
    void contextLoads_andActuatorHealthReturnsUp() {
        RestClient restClient = RestClient.create();
        ResponseEntity<String> response = restClient.get()
                .uri("http://localhost:" + port + "/actuator/health")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode())
                .as("Actuator /health should return HTTP 200")
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .as("Actuator health body should contain status UP")
                .contains("\"status\":\"UP\"");
    }
}
