package com.freepark.local.internalvehicle;

import java.util.UUID;

public record ImportInternalVehiclesResponse(UUID batchId, int imported, int skipped) {
}
