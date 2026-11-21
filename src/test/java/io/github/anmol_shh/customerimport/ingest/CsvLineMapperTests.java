package io.github.anmol_shh.customerimport.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.file.transform.IncorrectTokenCountException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CsvLineMapper}.
 *
 * <p>Runs as pure POJO tests without Spring context or Testcontainers.
 * Verified by inspecting spring-batch-infrastructure-6.0.6.
 */
class CsvLineMapperTests {

    private CsvLineMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CsvLineMapper();
    }

    @Test
    void mapsNormalRowSuccessfully() throws Exception {
        String line = "CUST-001,Alice Smith,alice@example.com,US,PREMIUM,2026-01-15";

        CsvRecord record = mapper.mapLine(line, 2);

        assertThat(record).isNotNull();
        assertThat(record.lineNumber()).isEqualTo(2);
        assertThat(record.externalId()).isEqualTo("CUST-001");
        assertThat(record.fullName()).isEqualTo("Alice Smith");
        assertThat(record.email()).isEqualTo("alice@example.com");
        assertThat(record.country()).isEqualTo("US");
        assertThat(record.plan()).isEqualTo("PREMIUM");
        assertThat(record.signupDate()).isEqualTo("2026-01-15");
    }

    @Test
    void mapsRowWithEmptyOptionalFields() throws Exception {
        String line = "CUST-002,Bob Jones,bob@example.com,,,2026-02-01";

        CsvRecord record = mapper.mapLine(line, 3);

        assertThat(record).isNotNull();
        assertThat(record.lineNumber()).isEqualTo(3);
        assertThat(record.externalId()).isEqualTo("CUST-002");
        assertThat(record.fullName()).isEqualTo("Bob Jones");
        assertThat(record.email()).isEqualTo("bob@example.com");
        assertThat(record.country()).isEmpty();
        assertThat(record.plan()).isEmpty();
        assertThat(record.signupDate()).isEqualTo("2026-02-01");
    }

    @Test
    void computesStartLineForRecordWithEmbeddedNewline() throws Exception {
        // FlatFileItemReader passes the end line (5). Mapper calculates start line = 5 - 1 = 4.
        String line = "CUST-003,\"Charlie\nBrown\",charlie@example.com,CA,ENTERPRISE,2026-03-10";

        CsvRecord record = mapper.mapLine(line, 5);

        assertThat(record).isNotNull();
        assertThat(record.lineNumber()).isEqualTo(4);
        assertThat(record.externalId()).isEqualTo("CUST-003");
        assertThat(record.fullName()).isEqualTo("Charlie\nBrown");
        assertThat(record.email()).isEqualTo("charlie@example.com");
        assertThat(record.country()).isEqualTo("CA");
        assertThat(record.plan()).isEqualTo("ENTERPRISE");
        assertThat(record.signupDate()).isEqualTo("2026-03-10");
    }

    @Test
    void strictModeThrowsOnIncorrectTokenCount() {
        // DelimitedLineTokenizer in strict mode throws IncorrectTokenCountException when
        // token count does not match the configured column names count (6).
        String tooFewTokens = "CUST-004,David,david@example.com";
        assertThatThrownBy(() -> mapper.mapLine(tooFewTokens, 6))
                .isInstanceOf(IncorrectTokenCountException.class)
                .satisfies(ex -> {
                    IncorrectTokenCountException itce = (IncorrectTokenCountException) ex;
                    assertThat(itce.getExpectedCount()).isEqualTo(6);
                    assertThat(itce.getActualCount()).isEqualTo(3);
                });

        String tooManyTokens = "CUST-005,Eva,eva@example.com,FR,BASIC,2026-04-01,EXTRA_COLUMN";
        assertThatThrownBy(() -> mapper.mapLine(tooManyTokens, 7))
                .isInstanceOf(IncorrectTokenCountException.class)
                .satisfies(ex -> {
                    IncorrectTokenCountException itce = (IncorrectTokenCountException) ex;
                    assertThat(itce.getExpectedCount()).isEqualTo(6);
                    assertThat(itce.getActualCount()).isEqualTo(7);
                });
    }
}
