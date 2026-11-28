package com.chua.starter.datapool.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.task.backup.BackupVerifier;
import com.chua.common.support.task.backup.RestorePreflight;
import com.chua.starter.datapool.support.backup.BackupEventStreamRegistry;
import com.chua.starter.datapool.support.backup.DatabaseBackupService;
import com.chua.starter.datapool.support.entity.MsBackupHistory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 数据库备份中心 REST 接口。
 *
 * <p>归属 {@code spring-support-datapool-starter}，面向数据中心备份页面：
 * 查询可备份目标/配置/状态/历史、触发备份与恢复（危险操作需显式 {@code confirm=true}）、
 * 恢复前预检与试运行、离线校验备份产物、清理备份文件，以及订阅备份进度的 SSE 实时流
 * （{@code /stream}，事件帧口径见 {@link BackupEventStreamRegistry}）。</p>
 *
 * <p>同时兼容新路径 {@code /ms/datasource/database-backup} 与历史路径
 * {@code /ms/monitor/database-backup}。</p>
 *
 * @author CH
 * @since 2026/11/24
 */
@Slf4j
@Tag(name = "数据源中心", description = "数据库备份中心")
@RestController
@RequestMapping({"/ms/datasource/database-backup", "/ms/monitor/database-backup"})
@RequiredArgsConstructor
public class DatabaseBackupController {

    /**
     * 备份中心服务
     */
    private final DatabaseBackupService databaseBackupService;

    /**
     * 备份事件流注册中心
     */
    private final BackupEventStreamRegistry backupEventStreamRegistry;

    /**
     * 可备份目标列表（宿主默认主库 + 数据源中心已启用 JDBC 数据源）。
     *
     * @return 目标列表
     */
    @Operation(summary = "可备份目标列表")
    @GetMapping("/targets")
    public ReturnResult<List<DatabaseBackupService.BackupTarget>> targets() {
        return call("读取备份目标列表", databaseBackupService::targets);
    }

    /**
     * 备份配置（连接密码不外泄；支持按数据源 ID 切换）。
     *
     * @param datasourceId 数据源中心 ID（可选，为空回落宿主默认库）
     * @return 配置字段表
     */
    @Operation(summary = "备份配置")
    @GetMapping("/config")
    public ReturnResult<Map<String, Object>> config(
            @RequestParam(required = false) Long datasourceId) {
        return call("读取备份配置", () -> databaseBackupService.config(datasourceId));
    }

    /**
     * 当前任务状态（running/phase/进度等，含 SSE 订阅数）。
     *
     * @return 状态字段表
     */
    @Operation(summary = "当前任务状态")
    @GetMapping("/status")
    public ReturnResult<Map<String, Object>> status() {
        return call("读取任务状态", databaseBackupService::status);
    }

    /**
     * 备份历史（按落库时间倒序，支持按数据源 ID 或库名过滤）。
     *
     * @param limit        最多返回条数（1~500，默认 50）
     * @param datasourceId 数据源中心 ID（可选）
     * @param dbName       数据库名（可选，优先于 datasourceId）
     * @return 历史记录列表
     */
    @Operation(summary = "备份历史")
    @GetMapping("/history")
    public ReturnResult<List<MsBackupHistory>> history(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String dbName) {
        return call("查询备份历史", () -> databaseBackupService.history(limit, datasourceId, dbName));
    }

    /**
     * 触发一次数据库备份（异步执行，进度经 {@code /stream} 推送）。
     *
     * @param datasourceId 数据源中心 ID（可选，为空回落宿主默认库）
     * @return 是否受理
     */
    @Operation(summary = "触发备份")
    @PostMapping("/run")
    public ReturnResult<Boolean> run(@RequestParam(required = false) Long datasourceId) {
        return call("触发备份", () -> {
            databaseBackupService.triggerBackup(datasourceId);
            return true;
        });
    }

    /**
     * 触发一次恢复（危险操作：必须显式 {@code confirm=true}）。
     *
     * @param params 恢复参数
     * @return 是否受理
     */
    @Operation(summary = "触发恢复")
    @PostMapping("/restore")
    public ReturnResult<Boolean> restore(@RequestBody DatabaseBackupService.RestoreParams params) {
        return call("触发恢复", () -> {
            databaseBackupService.triggerRestore(params);
            return true;
        });
    }

    /**
     * 恢复前预检 / 试运行（只读或事务内回滚，不落业务数据）。
     *
     * @param params 预检参数
     * @return 预检结果
     */
    @Operation(summary = "恢复预检/试运行")
    @PostMapping("/preflight")
    public ReturnResult<RestorePreflight.PreflightResult> preflight(
            @RequestBody DatabaseBackupService.PreflightParams params) {
        return call("恢复预检", () -> databaseBackupService.preflight(params));
    }

    /**
     * 离线校验备份产物完整性（按清单核对字节数与 SHA-256）。
     *
     * @param target 备份目录、.sql 脚本或 .zip 归档路径
     * @return 校验结果
     */
    @Operation(summary = "校验备份产物")
    @PostMapping("/verify")
    public ReturnResult<BackupVerifier.VerificationResult> verify(@RequestParam String target) {
        return call("校验备份产物", () -> databaseBackupService.verify(target));
    }

    /**
     * 删除备份产物（仅限备份目录内 {@code database-*} 命名空间与 manifest）。
     *
     * @param target 相对备份根目录的路径
     * @return 删除的文件数
     */
    @Operation(summary = "删除备份产物")
    @DeleteMapping("/files")
    public ReturnResult<Integer> delete(@RequestParam String target) {
        return call("删除备份产物", () -> databaseBackupService.deleteBackup(target));
    }

    /**
     * 备份 / 恢复进度实时流（SSE）。
     *
     * @return SSE 流
     */
    @Operation(summary = "备份进度实时流(SSE)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(backupEventStreamRegistry.subscribe());
    }

    /**
     * 统一异常包装。
     *
     * @param action   操作描述
     * @param supplier 业务调用
     * @param <T>      返回类型
     * @return 响应
     */
    private <T> ReturnResult<T> call(String action, Supplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[database-backup] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}
