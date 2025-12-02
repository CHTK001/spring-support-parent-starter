package com.chua.starter.strategy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.strategy.entity.SysLimitRecord;
import com.chua.starter.strategy.service.SysLimitRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 限流记录控制器
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Tag(name = "限流记录管理")
@RestController
@RequestMapping("/v2/strategy/limit-record")
@RequiredArgsConstructor
public class SysLimitRecordController {

    private final SysLimitRecordService sysLimitRecordService;

    /**
     * 分页查询限流记录
     *
     * @param page 分页参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询限流记录")
    @GetMapping("/page")
    public Result<IPage<SysLimitRecord>> pageForLimitRecord(Page<SysLimitRecord> page) {
        return Result.success(sysLimitRecordService.page(page));
    }

    /**
     * 根据ID查询限流记录
     *
     * @param id 记录ID
     * @return 限流记录
     */
    @Operation(summary = "根据ID查询限流记录")
    @GetMapping("/{id}")
    public Result<SysLimitRecord> getByIdForLimitRecord(@PathVariable Long id) {
        return Result.success(sysLimitRecordService.getById(id));
    }

    /**
     * 删除限流记录
     *
     * @param id 记录ID
     * @return 操作结果
     */
    @Operation(summary = "删除限流记录")
    @DeleteMapping("/delete")
    public Result<Boolean> deleteForLimitRecord(@RequestParam Long id) {
        return Result.success(sysLimitRecordService.removeById(id));
    }

    /**
     * 清理指定天数之前的限流记录
     *
     * @param days 天数，默认30天
     * @return 清理的记录数
     */
    @Operation(summary = "清理指定天数之前的限流记录")
    @DeleteMapping("/clean")
    public Result<Integer> cleanOldRecordsForLimit(@RequestParam(defaultValue = "30") int days) {
        return Result.success(sysLimitRecordService.cleanOldRecords(days));
    }
}
