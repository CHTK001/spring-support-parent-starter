package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.service.SysAiUsageService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
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
    public ReturnResult<List<SysAiUsage>> list(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<SysAiUsage> list = usageService.list(groupName, provider, model, startTime, endTime, page, size);
        return ReturnResult.ok(list);
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
}
