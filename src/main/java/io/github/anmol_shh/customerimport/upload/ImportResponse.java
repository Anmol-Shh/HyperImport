package io.github.anmol_shh.customerimport.upload;

import java.util.UUID;

/**
 * Response payload returned upon completion of a synchronous CSV import request.
 *
 * <p>In Week 2, when execution goes asynchronous, this endpoint will return HTTP 202 Accepted.
 */
public record ImportResponse(
        UUID importId,
        String status,
        long rowsStaged
) {
}
