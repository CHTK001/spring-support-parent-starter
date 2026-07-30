package com.chua.starter.protocol.support.controller.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.protocol.firewall.FirewallMetrics;
import com.chua.starter.protocol.support.entity.SystemServerLog;
import com.chua.starter.protocol.support.service.server.SystemServerLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 协议管理-过滤器日志控制器。
 */
@Slf4j
@RestController
@RequestMapping("/service/systemserver-log")
@Api(tags = "协议管理-过滤器日志")
@Tag(name = "协议管理-过滤器日志")
@RequiredArgsConstructor
public class SystemServerLogController {

    private final SystemServerLogService logService;

    @GetMapping("/page")
    @Operation(summary = "分页查询过滤器日志")
    @ApiOperation("分页查询过滤器日志")
    public ReturnResult<IPage<SystemServerLog>> page(
            @RequestParam(value = "current", defaultValue = "1") @Parameter(description = "当前页") Long current,
            @RequestParam(value = "size", defaultValue = "20") @Parameter(description = "页大小") Long size,
            @RequestParam(value = "serverId", required = false) Integer serverId,
            @RequestParam(value = "filterType", required = false) String filterType,
            @RequestParam(value = "processStatus", required = false) String processStatus,
            @RequestParam(value = "clientIp", required = false) String clientIp,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        Page<SystemServerLog> page = new Page<>(current, size);
        return logService.pageLogs(page, serverId, filterType, processStatus, clientIp, startTime, endTime);
    }

    @GetMapping("/firewall/events")
    @Operation(summary = "查询防火墙事件")
    @ApiOperation("查询防火墙事件")
    public ReturnResult<List<Map<String, Object>>> firewallEvents(
            @RequestParam("serverId") Integer serverId,
            @RequestParam(value = "clientIp", required = false) String clientIp,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return logService.queryFirewallEvents(serverId, clientIp, action, startTime, endTime, limit);
    }

    @GetMapping("/firewall/metrics")
    @Operation(summary = "查询防火墙指标")
    @ApiOperation("查询防火墙指标")
    public ReturnResult<FirewallMetrics> firewallMetrics(
            @RequestParam("serverId") Integer serverId,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        return logService.queryFirewallMetrics(serverId, startTime, endTime);
    }

    @GetMapping("/export")
    @Operation(summary = "导出过滤器日志为CSV")
    @ApiOperation("导出过滤器日志为CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(value = "serverId", required = false) Integer serverId,
            @RequestParam(value = "filterType", required = false) String filterType,
            @RequestParam(value = "processStatus", required = false) String processStatus,
            @RequestParam(value = "clientIp", required = false) String clientIp,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        ReturnResult<byte[]> result = logService.exportCsv(serverId, filterType, processStatus, clientIp, startTime, endTime);
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(result.getMsg().getBytes());
        }
        String filename = "systemserver-log-" + System.currentTimeMillis() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        return ResponseEntity.ok().headers(headers).body(result.getData());
    }

    @DeleteMapping("/cleanup")
    @Operation(summary = "清理历史日志")
    @ApiOperation("清理历史日志")
    public ReturnResult<Integer> cleanup(
            @RequestParam("beforeTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beforeTime
    ) {
        return logService.cleanup(beforeTime);
    }
}
