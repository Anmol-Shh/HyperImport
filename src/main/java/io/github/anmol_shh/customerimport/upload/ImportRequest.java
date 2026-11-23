package io.github.anmol_shh.customerimport.upload;

import java.util.UUID;

/**
 * Plain record representing an upload import request tracked in {@code import_request}.
 */
public record ImportRequest(
        UUID importId,
        String originalFilename,
        String storedPath,
        Long jobExecutionId
) {
}
