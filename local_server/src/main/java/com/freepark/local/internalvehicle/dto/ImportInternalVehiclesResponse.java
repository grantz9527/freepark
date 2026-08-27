package com.freepark.local.internalvehicle.dto;

import java.util.UUID;

public record ImportInternalVehiclesResponse(UUID batchId, int imported, int skipped) {
}
