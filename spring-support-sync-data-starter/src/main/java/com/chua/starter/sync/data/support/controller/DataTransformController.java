package com.chua.starter.sync.data.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.data.support.entity.MonitorSyncTransformRule;
import com.chua.starter.sync.data.support.service.MonitorSyncTransformRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据转换 Controller
 *
 * @author System
 * @since 2026/03/09
 */
@RestController
@RequestMapping("/v1/sync/transform")
@Tag(name = "数据转换管理")
@RequiredArgsConstructor
public class DataTransformController {

    private final MonitorSyncTransformRuleService transformRuleService;

    /**
     * 获取转换规则列表
     */
    @GetMapping("/rules")
    @Operation(summary = "获取转换规则列表")
    public ReturnResult<List<MonitorSyncTransformRule>> listRules() {
        List<MonitorSyncTransformRule> rules = transformRuleService.list();
        return ReturnResult.ok(rules);
    }

    /**
     * 创建转换规则
     */
    @PostMapping("/rules")
    @Operation(summary = "创建转换规则")
    public ReturnResult<Long> createRule(@RequestBody MonitorSyncTransformRule rule) {
        boolean success = transformRuleService.save(rule);
        return success ? ReturnResult.ok(rule.getRuleId()) : ReturnResult.error("创建失败");
    }

    /**
     * 更新转换规则
     */
    @PutMapping("/rules/{ruleId}")
    @Operation(summary = "更新转换规则")
    public ReturnResult<Boolean> updateRule(
            @Parameter(description = "规则ID") @PathVariable Long ruleId,
            @RequestBody MonitorSyncTransformRule rule) {
        rule.setRuleId(ruleId);
        boolean success = transformRuleService.updateById(rule);
        return ReturnResult.ok(success);
    }

    /**
     * 删除转换规则
     */
    @DeleteMapping("/rules/{ruleId}")
    @Operation(summary = "删除转换规则")
    public ReturnResult<Boolean> deleteRule(
            @Parameter(description = "规则ID") @PathVariable Long ruleId) {
        boolean success = transformRuleService.removeById(ruleId);
        return ReturnResult.ok(success);
    }

    /**
     * 测试转换规则
     */
    @PostMapping("/rules/test")
    @Operation(summary = "测试转换规则")
    public ReturnResult<Map<String, Object>> testRule(
            @Parameter(description = "规则ID") @RequestParam Long ruleId,
            @Parameter(description = "测试数据") @RequestBody Map<String, Object> testData) {
        Map<String, Object> result = transformRuleService.testRule(ruleId, testData);
        return ReturnResult.ok(result);
    }
}
