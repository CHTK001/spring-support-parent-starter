package com.chua.starter.ai.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.datasearch.usage.spi.UsageParser;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.schedule.UsageAggregationTask;
import com.chua.starter.ai.support.service.SysAiUsageService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 用量控制器
 * 提供用量查询和统计接口
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/usage")
@RequiredArgsConstructor
@Tag(name = "AI 用量", description = "查询 AI 调用用量统计")
public class UsageController {

    /**
     * 用量服务
     */
    private final SysAiUsageService usageService;

    /**
     * Spring 应用上下文（用于收集 Spring 容器中的 UsageParser 实现）
     */
    @Autowired
    private ApplicationContext applicationContext;

    @Permission("admin")
    @Operation(summary = "用量列表", description = "分页查询用量明细")
    @GetMapping("/list")
    /**
     * List
     * @param groupName groupName
     * @param provider provider
     * @param model model
     * @param startTime startTime
     * @param endTime endTime
     * @param page page
     * @param size size
     * @param provider provider
     * @param model model
     * @param startTime startTime
     * @param endTime endTime
     * @param page page
     * @param size size
     * @param groupName groupName
     * @param provider provider
     * @param model model
     * @param startTime startTime
     * @param endTime endTime
     * @param provider provider
     * @param model model
     * @param startTime startTime
     * @param endTime endTime
     */
    public ReturnResult<?> list(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<?> p = usageService.list(groupName, provider, model, startTime, endTime, page, size);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("records", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", p.getCurrent());
        result.put("pageSize", p.getSize());
        return ReturnResult.ok(result);
    }

    @Permission("admin")
    @Operation(summary = "用量统计", description = "汇总统计")
    @GetMapping("/statistics")
    /**
     * Statistics
     * @param groupName groupName
     * @param provider provider
     * @param model model
     * @param startTime startTime
     * @param endTime endTime
     * @param provider provider
     * @param model model
     * @param startTime startTime
     * @param endTime endTime
     */
    public ReturnResult<Map<String, Object>> statistics(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        SysAiUsageService.UsageStatistics stats = usageService.getStatistics(groupName, provider, model, startTime, endTime);
        return ReturnResult.ok(Map.of(
                "totalCalls", stats.totalCalls,
                "totalPromptTokens", stats.totalPromptTokens,
                "totalCompletionTokens", stats.totalCompletionTokens,
                "totalTokens", stats.totalTokens,
                "totalCost", stats.totalCost,
                "totalCachedTokens", stats.totalCachedTokens,
                "avgLatency", stats.avgLatency,
                "standardCost", stats.standardCost
        ));
    }

    @Permission("admin")
    @Operation(summary = "用量数据源列表", description = "返回已注册的 UsageParser SPI 实现列表")
    @GetMapping("/parsers")
    public ReturnResult<List<Map<String, Object>>> parsers() {
        Map<String, UsageParser> allParsers = new LinkedHashMap<>();

        // 1. 优先从 Spring 容器收集（如系统默认的 SystemUsageParser）
        try {
            Map<String, UsageParser> springBeans = applicationContext.getBeansOfType(UsageParser.class);
            for (UsageParser parser : springBeans.values()) {
                allParsers.putIfAbsent(parser.name(), parser);
            }
        } catch (Exception e) {
            log.warn("[Usage] 收集 Spring UsageParser 失败: {}", e.getMessage());
        }

        // 2. 再收集 SPI 注册的外部工具解析器（opencode/codex 等）
        try {
            Map<String, UsageParser> spiParsers = ServiceProvider.of(UsageParser.class).list();
            if (spiParsers != null) {
                for (Map.Entry<String, UsageParser> entry : spiParsers.entrySet()) {
                    allParsers.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("[Usage] SPI 收集 UsageParser 失败: {}", e.getMessage());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, UsageParser> e : allParsers.entrySet()) {
            Map<String, Object> item = Map.of(
                    "name", e.getKey(),
                    "type", e.getValue().getClass().getSimpleName(),
                    "enabled", true
            );
            result.add(item);
        }
        return ReturnResult.ok(result);
    }

    @Permission("admin")
    @Operation(summary = "用量同步日志", description = "返回最近一次定时同步产生的日志")
    @GetMapping("/logs")
    public ReturnResult<List<UsageAggregationTask.SyncLogEntry>> logs() {
        return ReturnResult.ok(UsageAggregationTask.getSyncLogs());
    }
}
