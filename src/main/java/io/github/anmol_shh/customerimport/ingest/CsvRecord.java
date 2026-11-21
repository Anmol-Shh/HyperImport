package io.github.anmol_shh.customerimport.ingest;

/**
 * Raw CSV record representing a single customer entry from the input file.
 *
 * <p>Contains unvalidated string values as parsed from the CSV and the physical line number
 * where the record starts in the source file. No database or import identifiers are included here.
 */
public record CsvRecord(
        int lineNumber,
        String externalId,
        String fullName,
        String email,
        String country,
        String plan,
        String signupDate
) {
}
