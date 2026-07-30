package com.chua.starter.protocol.support.controller.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.service.server.SystemServerRuntimeLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 协议管理-系统服务器运行时日志（生命周期日志）控制器。
 *
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/service/systemserver-runtime-log")
@Api(tags = "协议管理-系统运行日志")
@Tag(name = "协议管理-系统运行日志")
@RequiredArgsConstructor
public class SystemServerRuntimeLogController {

    private final SystemServerRuntimeLogService runtimeLogService;

    @GetMapping("/page")
    @Operation(summary = "分页查询系统运行日志（文件）")
    @ApiOperation("分页查询系统运行日志（文件）")
    public ReturnResult<IPage<Map<String, Object>>> page(
            @RequestParam(value = "current", defaultValue = "1") @Parameter(description = "当前页") Long current,
            @RequestParam(value = "size", defaultValue = "50") @Parameter(description = "页大小") Long size,
            @RequestParam(value = "serverId", required = false) Integer serverId,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "event", required = false) String event,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        Page<?> page = new Page<>(current, size);
        return runtimeLogService.page(page, serverId, level, event, startTime, endTime, keyword);
    }
}

