package com.chua.starter.strategy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.strategy.entity.SysCircuitBreakerRecord;
import com.chua.starter.strategy.service.SysCircuitBreakerRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 熔断记录控制器
 * 提供熔断记录的查询接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@RestController
@RequestMapping("/v2/strategy/circuit-breaker-record")
@RequiredArgsConstructor
@Tag(name = "熔断记录管理", description = "熔断记录查询接口")
public class SysCircuitBreakerRecordController {

    private final SysCircuitBreakerRecordService circuitBreakerRecordService;

    /**
     * 分页查询熔断记录
     *
     * @param current 当前页
     * @param size    每页大小
     * @param entity  查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询熔断记录")
    public ReturnResult<IPage<SysCircuitBreakerRecord>> pageForStrategy(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size,
            SysCircuitBreakerRecord entity) {
        log.debug("分页查询熔断记录: current={}, size={}", current, size);
        IPage<SysCircuitBreakerRecord> page = new Page<>(current, size);
        return ReturnResult.ok(circuitBreakerRecordService.pageForStrategy(page, entity));
    }

    /**
     * 根据ID查询熔断记录
     *
     * @param id 记录ID
     * @return 记录详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询熔断记录")
    public ReturnResult<SysCircuitBreakerRecord> getByIdForStrategy(
            @Parameter(description = "记录ID") @PathVariable Long id) {
        log.debug("查询熔断记录: id={}", id);
        return ReturnResult.ok(circuitBreakerRecordService.getById(id));
    }

    /**
     * 删除熔断记录
     *
     * @param id 记录ID
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除熔断记录")
    public ReturnResult<Boolean> deleteForStrategy(
            @Parameter(description = "记录ID") @RequestParam Long id) {
        log.info("删除熔断记录: id={}", id);
        return ReturnResult.ok(circuitBreakerRecordService.removeById(id));
    }

    /**
     * 清理指定天数之前的熔断记录
     *
     * @param days 天数
     * @return 清理的记录数
     */
    @DeleteMapping("/clean")
    @Operation(summary = "清理指定天数之前的熔断记录")
    public ReturnResult<Integer> cleanForStrategy(
            @Parameter(description = "天数，默认30天") @RequestParam(defaultValue = "30") Integer days) {
        log.info("清理 {} 天前的熔断记录", days);
        return ReturnResult.ok(circuitBreakerRecordService.cleanRecordsBefore(days));
    }
}
