package com.zija.system.internal;

import com.zija.system.SystemApi;

import java.time.OffsetDateTime;
import java.util.UUID;

record SystemInfoResponse(
        String application,
        String version,
        String status,
        UUID installationId,
        OffsetDateTime databaseTime
) {
    static SystemInfoResponse from(SystemApi.SystemSnapshot snapshot) {
        return new SystemInfoResponse(
                snapshot.application(),
                snapshot.version(),
                snapshot.status(),
                snapshot.installationId(),
                snapshot.databaseTime()
        );
    }
}
