package com.chua.starter.monitor.starter.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.starter.monitor.starter.entity.MonitorSysGenScript;
import com.chua.starter.monitor.starter.pojo.ScriptManagementDTO;
import com.chua.starter.monitor.starter.service.ScriptManagementService;
import com.chua.starter.web.support.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 脚本管理控制器
 * 
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor/script")
@RequiredArgsConstructor
@Validated
@Tag(name = "脚本管理", description = "脚本管理相关接口")
public class ScriptManagementController {

    private final ScriptManagementService scriptManagementService;

    /**
     * 分页查询脚本列表
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询脚本列表", description = "根据条件分页查询脚本列表")
    public Result<IPage<ScriptManagementDTO>> getScriptPage(@RequestBody ScriptManagementDTO.ScriptQueryDTO queryDTO) {
        try {
            IPage<ScriptManagementDTO> page = scriptManagementService.getScriptPage(queryDTO);
            return Result.ok(page);
        } catch (Exception e) {
            log.error("查询脚本列表失败", e);
            return Result.error("查询脚本列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取脚本详情
     */
    @GetMapping("/{scriptId}")
    @Operation(summary = "获取脚本详情", description = "根据脚本ID获取脚本详细信息")
    public Result<ScriptManagementDTO> getScriptById(
            @Parameter(description = "脚本ID") @PathVariable @NotNull Integer scriptId) {
        try {
            ScriptManagementDTO script = scriptManagementService.getScriptById(scriptId);
            if (script == null) {
                return Result.error("脚本不存在");
            }
            return Result.ok(script);
        } catch (Exception e) {
            log.error("获取脚本详情失败: scriptId={}", scriptId, e);
            return Result.error("获取脚本详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建脚本
     */
    @PostMapping
    @Operation(summary = "创建脚本", description = "创建新的脚本")
    public Result<ScriptManagementDTO> createScript(@Valid @RequestBody ScriptManagementDTO scriptDTO) {
        try {
            ScriptManagementDTO result = scriptManagementService.createScript(scriptDTO);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("创建脚本失败", e);
            return Result.error("创建脚本失败: " + e.getMessage());
        }
    }

    /**
     * 更新脚本
     */
    @PutMapping
    @Operation(summary = "更新脚本", description = "更新现有脚本信息")
    public Result<ScriptManagementDTO> updateScript(@Valid @RequestBody ScriptManagementDTO scriptDTO) {
        try {
            ScriptManagementDTO result = scriptManagementService.updateScript(scriptDTO);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("更新脚本失败", e);
            return Result.error("更新脚本失败: " + e.getMessage());
        }
    }

    /**
     * 删除脚本
     */
    @DeleteMapping("/{scriptId}")
    @Operation(summary = "删除脚本", description = "根据脚本ID删除脚本")
    public Result<Boolean> deleteScript(@Parameter(description = "脚本ID") @PathVariable @NotNull Integer scriptId) {
        try {
            boolean result = scriptManagementService.deleteScript(scriptId);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("删除脚本失败: scriptId={}", scriptId, e);
            return Result.error("删除脚本失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除脚本
     */
    @DeleteMapping("/batch")
    @Operation(summary = "批量删除脚本", description = "批量删除多个脚本")
    public Result<Boolean> deleteScripts(@RequestBody List<Integer> scriptIds) {
        try {
            boolean result = scriptManagementService.deleteScripts(scriptIds);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("批量删除脚本失败", e);
            return Result.error("批量删除脚本失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用脚本
     */
    @PutMapping("/{scriptId}/status")
    @Operation(summary = "更新脚本状态", description = "启用或禁用脚本")
    public Result<Boolean> updateScriptStatus(@Parameter(description = "脚本ID") @PathVariable @NotNull Integer scriptId,
            @Parameter(description = "状态(0:禁用,1:启用)") @RequestParam @NotNull Integer status) {
        try {
            boolean result = scriptManagementService.updateScriptStatus(scriptId, status);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("更新脚本状态失败: scriptId={}, status={}", scriptId, status, e);
            return Result.error("更新脚本状态失败: " + e.getMessage());
        }
    }

    /**
     * 复制脚本
     */
    @PostMapping("/{scriptId}/copy")
    @Operation(summary = "复制脚本", description = "复制现有脚本创建新脚本")
    public Result<ScriptManagementDTO> copyScript(
            @Parameter(description = "脚本ID") @PathVariable @NotNull Integer scriptId,
            @Parameter(description = "新脚本名称") @RequestParam @NotNull String newScriptName) {
        try {
            ScriptManagementDTO result = scriptManagementService.copyScript(scriptId, newScriptName);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("复制脚本失败: scriptId={}, newName={}", scriptId, newScriptName, e);
            return Result.error("复制脚本失败: " + e.getMessage());
        }
    }

    /**
     * 执行脚本
     */
    @PostMapping("/execute")
    @Operation(summary = "执行脚本", description = "执行指定的脚本")
    public Result<ScriptManagementDTO.ScriptExecuteResponseDTO> executeScript(
            @Valid @RequestBody ScriptManagementDTO.ScriptExecuteRequestDTO requestDTO) {
        try {
            ScriptManagementDTO.ScriptExecuteResponseDTO result = scriptManagementService.executeScript(requestDTO);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("执行脚本失败", e);
            return Result.error("执行脚本失败: " + e.getMessage());
        }
    }

    /**
     * 停止脚本执行
     */
    @PostMapping("/execution/{executionId}/stop")
    @Operation(summary = "停止脚本执行", description = "停止正在执行的脚本")
    public Result<Boolean> stopScriptExecution(
            @Parameter(description = "执行记录ID") @PathVariable @NotNull Long executionId) {
        try {
            boolean result = scriptManagementService.stopScriptExecution(executionId);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("停止脚本执行失败: executionId={}", executionId, e);
            return Result.error("停止脚本执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取脚本执行历史
     */
    @GetMapping("/{scriptId}/executions")
    @Operation(summary = "获取脚本执行历史", description = "分页获取脚本的执行历史记录")
    public Result<IPage<ScriptManagementDTO.ScriptExecuteResponseDTO>> getExecutionHistory(
            @Parameter(description = "脚本ID") @PathVariable Integer scriptId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "页大小") @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            IPage<ScriptManagementDTO.ScriptExecuteResponseDTO> page = scriptManagementService
                    .getExecutionHistory(scriptId, pageNum, pageSize);
            return Result.ok(page);
        } catch (Exception e) {
            log.error("获取脚本执行历史失败: scriptId={}", scriptId, e);
            return Result.error("获取脚本执行历史失败: " + e.getMessage());
        }
    }

    /**
     * 获取执行详情
     */
    @GetMapping("/execution/{executionId}")
    @Operation(summary = "获取执行详情", description = "获取脚本执行的详细信息")
    public Result<ScriptManagementDTO.ScriptExecuteResponseDTO> getExecutionDetail(
            @Parameter(description = "执行记录ID") @PathVariable @NotNull Long executionId) {
        try {
            ScriptManagementDTO.ScriptExecuteResponseDTO result = scriptManagementService
                    .getExecutionDetail(executionId);
            if (result == null) {
                return Result.error("执行记录不存在");
            }
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取执行详情失败: executionId={}", executionId, e);
            return Result.error("获取执行详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取正在运行的脚本
     */
    @GetMapping("/executions/running")
    @Operation(summary = "获取正在运行的脚本", description = "获取当前正在执行的脚本列表")
    public Result<List<ScriptManagementDTO.ScriptExecuteResponseDTO>> getRunningExecutions() {
        try {
            List<ScriptManagementDTO.ScriptExecuteResponseDTO> result = scriptManagementService.getRunningExecutions();
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取正在运行的脚本失败", e);
            return Result.error("获取正在运行的脚本失败: " + e.getMessage());
        }
    }

    /**
     * 获取脚本统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取脚本统计信息", description = "获取脚本管理的统计数据")
    public Result<ScriptManagementDTO.ScriptStatisticsDTO> getScriptStatistics() {
        try {
            ScriptManagementDTO.ScriptStatisticsDTO result = scriptManagementService.getScriptStatistics();
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取脚本统计信息失败", e);
            return Result.error("获取脚本统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取支持的脚本类型
     */
    @GetMapping("/types")
    @Operation(summary = "获取支持的脚本类型", description = "获取系统支持的所有脚本类型")
    public Result<List<MonitorSysGenScript.ScriptType>> getSupportedScriptTypes() {
        try {
            List<MonitorSysGenScript.ScriptType> result = scriptManagementService.getSupportedScriptTypes();
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取支持的脚本类型失败", e);
            return Result.error("获取支持的脚本类型失败: " + e.getMessage());
        }
    }

    /**
     * 验证脚本名称是否可用
     */
    @GetMapping("/name/check")
    @Operation(summary = "验证脚本名称", description = "检查脚本名称是否可用")
    public Result<Boolean> checkScriptName(@Parameter(description = "脚本名称") @RequestParam @NotNull String scriptName,
            @Parameter(description = "排除的脚本ID") @RequestParam(required = false) Integer excludeId) {
        try {
            boolean available = scriptManagementService.isScriptNameAvailable(scriptName, excludeId);
            return Result.ok(available);
        } catch (Exception e) {
            log.error("验证脚本名称失败: scriptName={}", scriptName, e);
            return Result.error("验证脚本名称失败: " + e.getMessage());
        }
    }

    /**
     * 根据分类获取脚本
     */
    @GetMapping("/category/{category}")
    @Operation(summary = "根据分类获取脚本", description = "获取指定分类下的所有脚本")
    public Result<List<ScriptManagementDTO>> getScriptsByCategory(
            @Parameter(description = "脚本分类") @PathVariable @NotNull String category) {
        try {
            List<ScriptManagementDTO> result = scriptManagementService.getScriptsByCategory(category);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("根据分类获取脚本失败: category={}", category, e);
            return Result.error("根据分类获取脚本失败: " + e.getMessage());
        }
    }

    /**
     * 根据类型获取脚本
     */
    @GetMapping("/type/{scriptType}")
    @Operation(summary = "根据类型获取脚本", description = "获取指定类型的所有脚本")
    public Result<List<ScriptManagementDTO>> getScriptsByType(
            @Parameter(description = "脚本类型") @PathVariable @NotNull String scriptType) {
        try {
            List<ScriptManagementDTO> result = scriptManagementService.getScriptsByType(scriptType);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("根据类型获取脚本失败: scriptType={}", scriptType, e);
            return Result.error("根据类型获取脚本失败: " + e.getMessage());
        }
    }

    /**
     * 根据标签搜索脚本
     */
    @GetMapping("/search/tag")
    @Operation(summary = "根据标签搜索脚本", description = "根据标签搜索相关脚本")
    public Result<List<ScriptManagementDTO>> searchScriptsByTag(
            @Parameter(description = "标签") @RequestParam @NotNull String tag) {
        try {
            List<ScriptManagementDTO> result = scriptManagementService.searchScriptsByTag(tag);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("根据标签搜索脚本失败: tag={}", tag, e);
            return Result.error("根据标签搜索脚本失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近执行的脚本
     */
    @GetMapping("/recent")
    @Operation(summary = "获取最近执行的脚本", description = "获取最近执行过的脚本列表")
    public Result<List<ScriptManagementDTO>> getRecentExecutedScripts(
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "10") Integer limit) {
        try {
            List<ScriptManagementDTO> result = scriptManagementService.getRecentExecutedScripts(limit);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取最近执行的脚本失败", e);
            return Result.error("获取最近执行的脚本失败: " + e.getMessage());
        }
    }

    /**
     * 获取热门脚本
     */
    @GetMapping("/popular")
    @Operation(summary = "获取热门脚本", description = "获取执行次数最多的脚本列表")
    public Result<List<ScriptManagementDTO>> getPopularScripts(
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "10") Integer limit) {
        try {
            List<ScriptManagementDTO> result = scriptManagementService.getPopularScripts(limit);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取热门脚本失败", e);
            return Result.error("获取热门脚本失败: " + e.getMessage());
        }
    }

    /**
     * 导出脚本
     */
    @GetMapping("/{scriptId}/export")
    @Operation(summary = "导出脚本", description = "导出脚本为JSON格式")
    public Result<String> exportScript(@Parameter(description = "脚本ID") @PathVariable @NotNull Integer scriptId) {
        try {
            String result = scriptManagementService.exportScript(scriptId);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("导出脚本失败: scriptId={}", scriptId, e);
            return Result.error("导出脚本失败: " + e.getMessage());
        }
    }

    /**
     * 导入脚本
     */
    @PostMapping("/import")
    @Operation(summary = "导入脚本", description = "从JSON格式导入脚本")
    public Result<ScriptManagementDTO> importScript(@RequestBody String scriptContent) {
        try {
            ScriptManagementDTO result = scriptManagementService.importScript(scriptContent);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("导入脚本失败", e);
            return Result.error("导入脚本失败: " + e.getMessage());
        }
    }

    /**
     * 验证脚本语法
     */
    @PostMapping("/validate")
    @Operation(summary = "验证脚本语法", description = "验证脚本语法是否正确")
    public Result<Boolean> validateScriptSyntax(
            @Parameter(description = "脚本类型") @RequestParam @NotNull String scriptType,
            @RequestBody String scriptContent) {
        try {
            boolean result = scriptManagementService.validateScriptSyntax(scriptType, scriptContent);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("验证脚本语法失败: scriptType={}", scriptType, e);
            return Result.error("验证脚本语法失败: " + e.getMessage());
        }
    }

    /**
     * 获取脚本模板
     */
    @GetMapping("/template/{scriptType}")
    @Operation(summary = "获取脚本模板", description = "获取指定类型的脚本模板")
    public Result<String> getScriptTemplate(@Parameter(description = "脚本类型") @PathVariable @NotNull String scriptType) {
        try {
            String result = scriptManagementService.getScriptTemplate(scriptType);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取脚本模板失败: scriptType={}", scriptType, e);
            return Result.error("获取脚本模板失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期的执行记录
     */
    @DeleteMapping("/executions/cleanup")
    @Operation(summary = "清理过期执行记录", description = "清理指定天数之前的执行记录")
    public Result<Integer> cleanExpiredExecutions(
            @Parameter(description = "保留天数") @RequestParam(defaultValue = "30") Integer days) {
        try {
            int result = scriptManagementService.cleanExpiredExecutions(days);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("清理过期执行记录失败: days={}", days, e);
            return Result.error("清理过期执行记录失败: " + e.getMessage());
        }
    }
}
