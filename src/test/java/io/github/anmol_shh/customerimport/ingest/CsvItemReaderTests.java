package io.github.anmol_shh.customerimport.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.separator.DefaultRecordSeparatorPolicy;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reader-level integration test verifying {@link FlatFileItemReader} configured with {@link CsvLineMapper}
 * against a real classpath CSV fixture.
 *
 * <p>Runs as a pure unit test without Spring context or Testcontainers.
 * Verified by inspecting spring-batch-infrastructure-6.0.6.
 */
class CsvItemReaderTests {

    @Test
    void readsClasspathCsvFixtureWithMultilineRecord() throws Exception {
        FlatFileItemReader<CsvRecord> reader = new FlatFileItemReader<>(new CsvLineMapper());
        reader.setName("customerCsvReader");
        reader.setResource(new ClassPathResource("fixtures/customers.csv"));
        reader.setLinesToSkip(1);
        reader.setRecordSeparatorPolicy(new DefaultRecordSeparatorPolicy());

        ExecutionContext executionContext = new ExecutionContext();
        reader.open(executionContext);

        try {
            // Row 1 (physical line 2 in CSV file after header skip)
            CsvRecord record1 = reader.read();
            assertThat(record1).isNotNull();
            assertThat(record1.lineNumber()).isEqualTo(2);
            assertThat(record1.externalId()).isEqualTo("CUST-001");
            assertThat(record1.fullName()).isEqualTo("Alice Smith");
            assertThat(record1.email()).isEqualTo("alice@example.com");
            assertThat(record1.country()).isEqualTo("US");
            assertThat(record1.plan()).isEqualTo("PREMIUM");
            assertThat(record1.signupDate()).isEqualTo("2026-01-15");

            // Row 2 (physical line 3, quoted field containing a comma)
            CsvRecord record2 = reader.read();
            assertThat(record2).isNotNull();
            assertThat(record2.lineNumber()).isEqualTo(3);
            assertThat(record2.externalId()).isEqualTo("CUST-002");
            assertThat(record2.fullName()).isEqualTo("Smith, Bob");
            assertThat(record2.email()).isEqualTo("bob@example.com");
            assertThat(record2.country()).isEqualTo("UK");
            assertThat(record2.plan()).isEqualTo("BASIC");
            assertThat(record2.signupDate()).isEqualTo("2026-02-20");

            // Row 3 (spans physical lines 4 and 5 due to embedded newline inside quoted field)
            // FlatFileItemReader passes end line (5) because lineCount increments on every physical line read;
            // CsvLineMapper subtracts embedded newlines so lineNumber returns the start line (4).
            CsvRecord record3 = reader.read();
            assertThat(record3).isNotNull();
            assertThat(record3.lineNumber()).isEqualTo(4);
            assertThat(record3.externalId()).isEqualTo("CUST-003");
            assertThat(record3.fullName()).isEqualTo("Charlie\nBrown");
            assertThat(record3.email()).isEqualTo("charlie@example.com");
            assertThat(record3.country()).isEqualTo("CA");
            assertThat(record3.plan()).isEqualTo("ENTERPRISE");
            assertThat(record3.signupDate()).isEqualTo("2026-03-10");

            // End of file: assert read() returns null after the last record
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }
}
