package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.service.GlobalModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 全局模型控制器。
 *
 * <p>提供全局模型配置和 Bean 的查询接口，方便前端和其它模块使用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/global-model")
@RequiredArgsConstructor
@Tag(name = "全局模型服务", description = "全局默认模型配置和 Bean 查询接口")
public class GlobalModelController {

    /**
     * 全局模型服务
     */
    private final GlobalModelService globalModelService;

    /**
     * 获取配置摘要。
     *
     * @return 分组 → 配置摘要
     */
    @Operation(summary = "获取配置摘要", description = "获取所有默认模型分组的配置摘要")
    @GetMapping("/summary")
    public ReturnResult<Map<String, Map<String, String>>> summary() {
        return ReturnResult.ok(globalModelService.getConfigSummary());
    }

    /**
     * 获取配置值。
     *
     * @param group 分组
     * @param field 字段名
     * @return 配置值
     */
    @Operation(summary = "获取配置值", description = "获取指定分组指定字段的配置值")
    @GetMapping("/config")
    public ReturnResult<String> getConfig(
            @RequestParam String group,
            @RequestParam String field) {
        String value = globalModelService.getConfig(group, field);
        return ReturnResult.ok(value);
    }

    /**
     * 获取布尔类型配置值。
     *
     * @param group 分组
     * @param field 字段名
     * @param defaultValue 默认值
     * @return 配置值
     */
    @Operation(summary = "获取布尔类型配置值", description = "获取指定分组指定字段的布尔类型配置值")
    @GetMapping("/config/bool")
    public ReturnResult<Boolean> getBoolConfig(
            @RequestParam String group,
            @RequestParam String field,
            @RequestParam(defaultValue = "false") boolean defaultValue) {
        boolean value = globalModelService.getBoolConfig(group, field, defaultValue);
        return ReturnResult.ok(value);
    }

    /**
     * 获取整数类型配置值。
     *
     * @param group 分组
     * @param field 字段名
     * @param defaultValue 默认值
     * @return 配置值
     */
    @Operation(summary = "获取整数类型配置值", description = "获取指定分组指定字段的整数类型配置值")
    @GetMapping("/config/int")
    public ReturnResult<Integer> getIntConfig(
            @RequestParam String group,
            @RequestParam String field,
            @RequestParam(defaultValue = "0") int defaultValue) {
        int value = globalModelService.getIntConfig(group, field, defaultValue);
        return ReturnResult.ok(value);
    }
}
