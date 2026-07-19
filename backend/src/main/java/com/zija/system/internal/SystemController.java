package com.zija.system.internal;

import com.zija.system.SystemApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
class SystemController {

    private final SystemApi systemApi;

    SystemController(SystemApi systemApi) {
        this.systemApi = systemApi;
    }

    @GetMapping("/info")
    SystemInfoResponse info() {
        return SystemInfoResponse.from(systemApi.current());
    }
}
