package com.chua.starter.strategy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.strategy.entity.SysDebounceConfiguration;
import com.chua.starter.strategy.service.SysDebounceConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 防抖配置控制器
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Tag(name = "防抖配置管理")
@RestController
@RequestMapping("/v2/strategy/debounce")
@RequiredArgsConstructor
public class SysDebounceConfigurationController {

    private final SysDebounceConfigurationService sysDebounceConfigurationService;

    /**
     * 分页查询防抖配置
     *
     * @param page                      分页参数
     * @param sysDebounceConfiguration 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询防抖配置")
    @GetMapping("/page")
    public ReturnResult<IPage<SysDebounceConfiguration>> pageForDebounce(
            Page<SysDebounceConfiguration> page,
            SysDebounceConfiguration sysDebounceConfiguration) {
        return ReturnResult.success(sysDebounceConfigurationService.pageForDebounce(page, sysDebounceConfiguration));
    }

    /**
     * 查询所有防抖配置
     *
     * @return 防抖配置列表
     */
    @Operation(summary = "查询所有防抖配置")
    @GetMapping("/list")
    public ReturnResult<List<SysDebounceConfiguration>> listForDebounce() {
        return ReturnResult.success(sysDebounceConfigurationService.list());
    }

    /**
     * 查询所有启用的防抖配置
     *
     * @return 启用的防抖配置列表
     */
    @Operation(summary = "查询所有启用的防抖配置")
    @GetMapping("/enabled")
    public ReturnResult<List<SysDebounceConfiguration>> listEnabledForDebounce() {
        return ReturnResult.success(sysDebounceConfigurationService.listEnabledForDebounce());
    }

    /**
     * 根据ID查询防抖配置
     *
     * @param id 防抖配置ID
     * @return 防抖配置
     */
    @Operation(summary = "根据ID查询防抖配置")
    @GetMapping("/{id}")
    public ReturnResult<SysDebounceConfiguration> getByIdForDebounce(@PathVariable Long id) {
        return ReturnResult.success(sysDebounceConfigurationService.getById(id));
    }

    /**
     * 保存防抖配置
     *
     * @param sysDebounceConfiguration 防抖配置
     * @return 操作结果
     */
    @Operation(summary = "保存防抖配置")
    @PostMapping("/save")
    public ReturnResult<Boolean> saveForDebounce(@RequestBody SysDebounceConfiguration sysDebounceConfiguration) {
        return ReturnResult.success(sysDebounceConfigurationService.save(sysDebounceConfiguration));
    }

    /**
     * 更新防抖配置
     *
     * @param sysDebounceConfiguration 防抖配置
     * @return 操作结果
     */
    @Operation(summary = "更新防抖配置")
    @PutMapping("/update")
    public ReturnResult<Boolean> updateForDebounce(@RequestBody SysDebounceConfiguration sysDebounceConfiguration) {
        return ReturnResult.success(sysDebounceConfigurationService.updateById(sysDebounceConfiguration));
    }

    /**
     * 删除防抖配置
     *
     * @param id 防抖配置ID
     * @return 操作结果
     */
    @Operation(summary = "删除防抖配置")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> deleteForDebounce(@RequestParam Long id) {
        return ReturnResult.success(sysDebounceConfigurationService.removeById(id));
    }

    /**
     * 刷新防抖配置到内存
     *
     * @return 刷新的配置数量
     */
    @Operation(summary = "刷新防抖配置到内存")
    @PostMapping("/refresh")
    public ReturnResult<Integer> refreshForDebounce() {
        return ReturnResult.success(sysDebounceConfigurationService.refreshDebounceConfigurations());
    }
}
