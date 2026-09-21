package com.gzxm.server.modules.systemlog.api;

import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.modules.systemlog.application.SystemErrorLogService;
import com.gzxm.server.modules.systemlog.repository.SystemErrorLogMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/system-logs")
public class SystemErrorLogController {
    private final SystemErrorLogService service;
    public SystemErrorLogController(SystemErrorLogService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('system.manage')")
    public PageResult<SystemErrorLogMapper.Row> list(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String severity, @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) String username, @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return service.list(page, size, severity, statusCode, username, traceId, from, to);
    }
}
