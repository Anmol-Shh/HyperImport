package io.github.anmol_shh.customerimport.ingest;

import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;

/**
 * LineMapper that parses a delimited CSV line into a {@link CsvRecord}.
 *
 * <p>A custom LineMapper is required because {@code DefaultLineMapper} ignores the {@code lineNumber}
 * argument it receives and returns whatever its {@code FieldSetMapper} builds, losing line tracking.
 *
 * <p>Verified by inspecting spring-batch-infrastructure-6.0.6.
 */
public class CsvLineMapper implements LineMapper<CsvRecord> {

    public static final String[] COLUMN_NAMES = {
            "external_id",
            "full_name",
            "email",
            "country",
            "plan",
            "signup_date"
    };

    private final DelimitedLineTokenizer tokenizer;

    public CsvLineMapper() {
        this.tokenizer = new DelimitedLineTokenizer();
        this.tokenizer.setNames(COLUMN_NAMES);
        this.tokenizer.setStrict(true);
    }

    @Override
    public CsvRecord mapLine(String line, int lineNumber) throws Exception {
        // FlatFileItemReader passes the END line of a multi-line record (verified by bytecode inspection
        // of FlatFileItemReader in spring-batch-infrastructure-6.0.6). We compute and report the START line
        // (lineNumber minus embedded newlines) so failure reports in Week 2 accurately identify where the record begins.
        int startLineNumber = lineNumber - countOccurrences(line, '\n');

        FieldSet fieldSet = tokenizer.tokenize(line);

        return new CsvRecord(
                startLineNumber,
                fieldSet.readString("external_id"),
                fieldSet.readString("full_name"),
                fieldSet.readString("email"),
                fieldSet.readString("country"),
                fieldSet.readString("plan"),
                fieldSet.readString("signup_date")
        );
    }

    private static int countOccurrences(String text, char target) {
        if (text == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }
}
