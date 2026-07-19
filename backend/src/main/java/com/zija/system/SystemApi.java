package com.zija.system;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SystemApi {

    SystemSnapshot current();

    record SystemSnapshot(
            String application,
            String version,
            String status,
            UUID installationId,
            OffsetDateTime databaseTime
    ) {
    }
}
