package com.chua.starter.strategy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.strategy.entity.SysCircuitBreakerConfiguration;
import com.chua.starter.strategy.service.SysCircuitBreakerConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 熔断配置控制器
 * 提供熔断策略的 CRUD 接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@RestController
@RequestMapping("/v2/strategy/circuit-breaker")
@RequiredArgsConstructor
@Tag(name = "熔断配置管理", description = "熔断策略的配置管理接口")
public class SysCircuitBreakerConfigurationController {

    private final SysCircuitBreakerConfigurationService circuitBreakerService;

    /**
     * 分页查询熔断配置
     *
     * @param current 当前页
     * @param size    每页大小
     * @param entity  查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询熔断配置")
    public ReturnResult<IPage<SysCircuitBreakerConfiguration>> pageForStrategy(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size,
            SysCircuitBreakerConfiguration entity) {
        log.debug("分页查询熔断配置: current={}, size={}", current, size);
        IPage<SysCircuitBreakerConfiguration> page = new Page<>(current, size);
        return ReturnResult.ok(circuitBreakerService.pageForStrategy(page, entity));
    }

    /**
     * 查询熔断配置列表
     *
     * @return 配置列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询熔断配置列表")
    public ReturnResult<List<SysCircuitBreakerConfiguration>> listForStrategy() {
        log.debug("查询所有熔断配置");
        return ReturnResult.ok(circuitBreakerService.list());
    }

    /**
     * 查询启用的熔断配置
     *
     * @return 启用的配置列表
     */
    @GetMapping("/enabled")
    @Operation(summary = "查询启用的熔断配置")
    public ReturnResult<List<SysCircuitBreakerConfiguration>> listEnabledForStrategy() {
        log.debug("查询启用的熔断配置");
        return ReturnResult.ok(circuitBreakerService.listEnabledConfigurations());
    }

    /**
     * 根据ID查询熔断配置
     *
     * @param id 配置ID
     * @return 配置详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询熔断配置")
    public ReturnResult<SysCircuitBreakerConfiguration> getByIdForStrategy(
            @Parameter(description = "配置ID") @PathVariable Long id) {
        log.debug("查询熔断配置: id={}", id);
        return ReturnResult.ok(circuitBreakerService.getById(id));
    }

    /**
     * 保存熔断配置
     *
     * @param entity 配置数据
     * @return 保存结果
     */
    @PostMapping("/save")
    @Operation(summary = "保存熔断配置")
    public ReturnResult<SysCircuitBreakerConfiguration> saveForStrategy(@RequestBody SysCircuitBreakerConfiguration entity) {
        log.info("保存熔断配置: {}", entity.getSysCircuitBreakerName());
        boolean success = circuitBreakerService.save(entity);
        if (success) {
            // 保存成功后刷新熔断器
            circuitBreakerService.refreshCircuitBreakers();
            return ReturnResult.ok(entity);
        }
        return ReturnResult.error("保存熔断配置失败");
    }

    /**
     * 更新熔断配置
     *
     * @param entity 配置数据
     * @return 更新结果
     */
    @PutMapping("/update")
    @Operation(summary = "更新熔断配置")
    public ReturnResult<Boolean> updateForStrategy(@RequestBody SysCircuitBreakerConfiguration entity) {
        log.info("更新熔断配置: id={}, name={}", entity.getSysCircuitBreakerId(), entity.getSysCircuitBreakerName());
        boolean success = circuitBreakerService.updateById(entity);
        if (success) {
            // 更新成功后刷新熔断器
            circuitBreakerService.refreshCircuitBreakers();
        }
        return ReturnResult.ok(success);
    }

    /**
     * 删除熔断配置
     *
     * @param id 配置ID
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除熔断配置")
    public ReturnResult<Boolean> deleteForStrategy(
            @Parameter(description = "配置ID") @RequestParam Long id) {
        log.info("删除熔断配置: id={}", id);
        boolean success = circuitBreakerService.removeById(id);
        if (success) {
            // 删除成功后刷新熔断器
            circuitBreakerService.refreshCircuitBreakers();
        }
        return ReturnResult.ok(success);
    }

    /**
     * 刷新熔断配置
     *
     * @return 刷新结果
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新熔断配置到Resilience4j")
    public ReturnResult<Void> refreshForStrategy() {
        log.info("手动刷新熔断配置");
        circuitBreakerService.refreshCircuitBreakers();
        return ReturnResult.ok();
    }
}
