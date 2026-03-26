package com.chua.starter.proxy.support.controller.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerLog;
import com.chua.starter.proxy.support.service.server.SystemServerLogService;
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

@Slf4j
@RestController
@RequestMapping("/proxy/server/log")
@Api(tags = "服务管理-过滤器日志")
@Tag(name = "服务管理-过滤器日志")
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





