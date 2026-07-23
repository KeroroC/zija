package com.zija.system.internal;

import com.zija.system.SystemApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统信息控制器。
 *
 * <p>提供系统级别信息查询的 REST API 端点。</p>
 *
 * <ul>
 *   <li>{@code GET /api/v1/system/info} — 获取当前系统信息</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/system")
class SystemController {

    private final SystemApi systemApi;

    SystemController(SystemApi systemApi) {
        this.systemApi = systemApi;
    }

    /**
     * 获取当前系统信息。
     *
     * @return 系统信息响应
     */
    @GetMapping("/info")
    SystemInfoResponse info() {
        return SystemInfoResponse.from(systemApi.current());
    }
}
