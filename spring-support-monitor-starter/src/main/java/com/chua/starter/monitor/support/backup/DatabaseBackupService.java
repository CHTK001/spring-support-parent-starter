package com.chua.starter.monitor.support.backup;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.task.backup.BackupEvent;
import com.chua.common.support.task.backup.BackupListener;
import com.chua.common.support.task.backup.BackupManager;
import com.chua.common.support.task.backup.BackupManifest;
import com.chua.common.support.task.backup.BackupResult;
import com.chua.common.support.task.backup.BackupVerifier;
import com.chua.common.support.task.backup.DatabaseBackupConfig;
import com.chua.common.support.task.backup.DatabaseRestoreConfig;
import com.chua.common.support.task.backup.DatabaseRestoreResult;
import com.chua.common.support.task.backup.RestoreEvent;
import com.chua.common.support.task.backup.RestoreListener;
import com.chua.common.support.task.backup.RestorePreflight;
import com.chua.starter.monitor.support.entity.MsBackupHistory;
import com.chua.starter.monitor.support.mapper.MsBackupHistoryMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * 数据库备份中心服务。
 *
 * <p>能力门面是 utils 层 {@link BackupManager}（逻辑备份：导出建表语句 + 全量数据
 * INSERT 脚本）。本服务负责 monitor 侧的编排：</p>
 * <ul>
 *   <li>从宿主当前 Spring DataSource 解析「当前库」连接信息，保证备份对象与业务库一致；</li>
 *   <li>备份/恢复在单线程工作池异步执行，同一时间只允许一个任务（防并发）；</li>
 *   <li>进度经 {@link BackupEventStreamRegistry} 以 SSE 实时推送（不依赖队列与 socket 组件）；</li>
 *   <li>备份审计记录经 {@link DbBackupHistoryStore} 落 {@code ms_backup_history} 表。</li>
 * </ul>
 *
 * <p>恢复是危险操作：必须显式确认（confirm=true），且删除备份文件的接口只允许
 * 清理备份目录内 {@code database-*} 与 {@code manifest.json}（与 utils 保留策略同口径），
 * 防止误删任意文件。</p>
 *
 * @author CH
 * @since 2026/11/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseBackupService {

    /**
     * 事件类型：备份
     */
    public static final String TYPE_BACKUP = "backup";

    /**
     * 事件类型：恢复
     */
    public static final String TYPE_RESTORE = "restore";

    private final DatabaseBackupProperties properties;
    private final MsBackupHistoryMapper backupHistoryMapper;
    private final DbBackupHistoryStore dbBackupHistoryStore;
    private final BackupEventStreamRegistry eventStreamRegistry;
    private final ObjectProvider<DataSourceProperties> dataSourcePropertiesProvider;
    private final ObjectProvider<DataSource> dataSourceProvider;
    private final Environment environment;

    /**
     * utils 备份门面（无状态，可复用）。
     */
    private final BackupManager backupManager = new BackupManager();

    /**
     * 任务互斥锁：同一时间只允许一个备份/恢复任务在执行。
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 当前任务状态快照（volatile，供 {@code /status} 读取与前端轮询兜底）。
     */
    private volatile Map<String, Object> currentStatus = Map.of("running", false);

    /**
     * 备份/恢复工作线程池：单线程串行执行，守护线程不阻塞 JVM 退出。
     */
    private ExecutorService executor;

    /**
     * 初始化工作线程池。
     */
    @PostConstruct
    public void init() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "database-backup-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 释放工作线程池。
     */
    @PreDestroy
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // ==================== 查询 ====================

    /**
     * 当前任务状态快照（含 SSE 订阅数）。
     *
     * @return 状态字段表
     */
    public Map<String, Object> status() {
        Map<String, Object> snapshot = new LinkedHashMap<>(currentStatus);
        snapshot.put("sseSubscribers", eventStreamRegistry.subscriberCount());
        return snapshot;
    }

    /**
     * 备份配置视图（连接密码不外泄，jdbcUrl 中的密码段脱敏）。
     *
     * @return 配置字段表
     */
    public Map<String, Object> config() {
        String jdbcUrl;
        try {
            jdbcUrl = resolveConnection()[0];
        } catch (Exception e) {
            jdbcUrl = null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("enabled", properties.isEnabled());
        view.put("backupDir", backupDir().toString());
        view.put("retentionDays", properties.getRetentionDays());
        view.put("maxBackupCount", properties.getMaxBackupCount());
        view.put("consistentSnapshot", properties.isConsistentSnapshot());
        view.put("writeManifest", properties.isWriteManifest());
        view.put("jdbcUrl", maskJdbcUrl(jdbcUrl));
        view.put("dbName", extractDbName(jdbcUrl));
        view.put("running", running.get());
        return view;
    }

    /**
     * 查询备份历史（按落库时间倒序）。
     *
     * @param limit 最多返回条数（1~500）
     * @return 历史记录列表
     */
    public List<MsBackupHistory> history(int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return backupHistoryMapper.selectList(Wrappers.<MsBackupHistory>lambdaQuery()
                .orderByDesc(MsBackupHistory::getCreateTime)
                .orderByDesc(MsBackupHistory::getId)
                .last("LIMIT " + capped));
    }

    // ==================== 备份 ====================

    /**
     * 触发一次数据库备份（异步执行，进度经 SSE 推送）。
     *
     * <p>同一时间只允许一个备份/恢复任务；重复触发抛 {@link IllegalStateException}。</p>
     */
    public void triggerBackup() {
        checkEnabled();
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("已有备份/恢复任务在执行中，请等待完成后再试");
        }
        try {
            publishSubmitted(TYPE_BACKUP);
            executor.submit(this::doBackup);
        } catch (Exception e) {
            running.set(false);
            throw e;
        }
    }

    /**
     * 备份任务体：构建配置 → 交给 utils 门面执行；进度与终态由监听器广播。
     */
    private void doBackup() {
        try {
            BackupResult result = backupManager.backupDatabase(buildBackupConfig());
            if (result != null && !result.isSuccess()
                    && !BackupEvent.PHASE_FAILURE.equals(currentStatus.get("phase"))) {
                // 防御：策略返回失败但未触发 onFailure 回调时补发失败事件
                publishFailure(TYPE_BACKUP, result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("[database-backup] 备份任务异常", e);
            publishFailure(TYPE_BACKUP, e.getMessage());
        } finally {
            finishTask();
        }
    }

    /**
     * 构建数据库备份配置：连接信息 + 备份目录/保留策略 + 历史落库 + SSE 监听器。
     *
     * @return 备份配置
     */
    private DatabaseBackupConfig buildBackupConfig() {
        String[] conn = resolveConnection();
        return DatabaseBackupConfig.builder()
                .jdbcUrl(conn[0])
                .username(conn[1])
                .password(conn[2])
                .backupDir(backupDir())
                .retentionDays(properties.getRetentionDays())
                .maxBackupCount(properties.getMaxBackupCount())
                .consistentSnapshot(properties.isConsistentSnapshot())
                .writeManifest(properties.isWriteManifest())
                .historyStore(dbBackupHistoryStore)
                .listeners(List.of(backupListener()))
                .build();
    }

    /**
     * 备份事件监听器：把 utils 回调转成 SSE 广播与状态快照更新。
     *
     * @return 备份监听器
     */
    private BackupListener backupListener() {
        return new BackupListener() {
            @Override
            public void onStart(BackupEvent event) {
                publishBackupEvent(event);
            }

            @Override
            public void onTableDone(BackupEvent event) {
                publishBackupEvent(event);
            }

            @Override
            public void onSuccess(BackupEvent event) {
                publishBackupEvent(event);
            }

            @Override
            public void onFailure(BackupEvent event) {
                publishBackupEvent(event);
            }
        };
    }

    /**
     * 备份事件 → SSE 载荷（Path 等对象转字符串，避免序列化噪音）。
     *
     * @param event utils 备份事件
     */
    private void publishBackupEvent(BackupEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", TYPE_BACKUP);
        payload.put("phase", event.getPhase());
        payload.put("time", str(event.getTime()));
        payload.put("dbName", event.getDbName());
        payload.put("table", event.getTable());
        payload.put("tableRows", event.getTableRows());
        payload.put("totalRows", event.getTotalRows());
        payload.put("totalTables", event.getTotalTables());
        payload.put("finishedTables", event.getFinishedTables());
        payload.put("errorMessage", event.getErrorMessage());
        BackupResult result = event.getResult();
        if (result != null) {
            payload.put("success", result.isSuccess());
            payload.put("backupPath", str(result.getBackupPath()));
            payload.put("fileCount", result.getFileCount());
            payload.put("totalSize", result.getTotalSize());
            payload.put("durationMillis", result.getDurationMillis());
            if (result.getErrorMessage() != null) {
                payload.put("errorMessage", result.getErrorMessage());
            }
        }
        publish(payload);
    }

    // ==================== 恢复 ====================

    /**
     * 触发一次恢复（异步执行，进度经 SSE 推送）。
     *
     * <p>恢复会向目标库写入数据，必须显式确认（confirm=true）。</p>
     *
     * @param params 恢复参数
     */
    public void triggerRestore(RestoreParams params) {
        checkEnabled();
        if (!params.confirm()) {
            throw new IllegalArgumentException("恢复操作会写入目标库，必须显式传 confirm=true");
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("已有备份/恢复任务在执行中，请等待完成后再试");
        }
        try {
            publishSubmitted(TYPE_RESTORE);
            executor.submit(() -> doRestore(params));
        } catch (Exception e) {
            running.set(false);
            throw e;
        }
    }

    /**
     * 恢复任务体。
     *
     * @param params 恢复参数
     */
    private void doRestore(RestoreParams params) {
        try {
            String[] conn = resolveConnection();
            DatabaseRestoreConfig config = DatabaseRestoreConfig.builder()
                    .jdbcUrl(conn[0])
                    .username(conn[1])
                    .password(conn[2])
                    .scriptFile(Paths.get(params.scriptFile()))
                    .dropBeforeCreate(params.dropBeforeCreate())
                    .continueOnError(params.continueOnError())
                    .skipEmptyCheck(params.skipEmptyCheck())
                    .verifyChecksum(params.verifyChecksum())
                    .listeners(List.of(restoreListener()))
                    .build();
            DatabaseRestoreResult result = backupManager.restoreDatabase(config);
            if (result != null && !result.isSuccess()
                    && !RestoreEvent.PHASE_FAILURE.equals(currentStatus.get("phase"))) {
                // 防御：策略返回失败但未触发 onFailure 回调时补发失败事件
                publishFailure(TYPE_RESTORE, result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("[database-backup] 恢复任务异常", e);
            publishFailure(TYPE_RESTORE, e.getMessage());
        } finally {
            finishTask();
        }
    }

    /**
     * 恢复事件监听器：把 utils 回调转成 SSE 广播与状态快照更新。
     *
     * @return 恢复监听器
     */
    private RestoreListener restoreListener() {
        return new RestoreListener() {
            @Override
            public void onStart(RestoreEvent event) {
                publishRestoreEvent(event);
            }

            @Override
            public void onProgress(RestoreEvent event) {
                publishRestoreEvent(event);
            }

            @Override
            public void onSuccess(RestoreEvent event) {
                publishRestoreEvent(event);
            }

            @Override
            public void onFailure(RestoreEvent event) {
                publishRestoreEvent(event);
            }
        };
    }

    /**
     * 恢复事件 → SSE 载荷。
     *
     * @param event utils 恢复事件
     */
    private void publishRestoreEvent(RestoreEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", TYPE_RESTORE);
        payload.put("phase", event.getPhase());
        payload.put("time", str(event.getTime()));
        payload.put("currentFile", str(event.getCurrentFile()));
        payload.put("filesTotal", event.getFilesTotal());
        payload.put("filesDone", event.getFilesDone());
        payload.put("statementsProcessed", event.getStatementsProcessed());
        payload.put("statementsExecuted", event.getStatementsExecuted());
        payload.put("statementsSkipped", event.getStatementsSkipped());
        payload.put("dryRun", event.isDryRun());
        payload.put("message", event.getMessage());
        payload.put("errorMessage", event.getErrorMessage());
        DatabaseRestoreResult result = event.getResult();
        if (result != null) {
            payload.put("success", result.isSuccess());
            payload.put("statements", result.getStatements());
            payload.put("skippedStatements", result.getSkippedStatements());
            payload.put("droppedTables", result.getDroppedTables());
            payload.put("durationMs", result.getDurationMs());
            if (result.getErrorMessage() != null) {
                payload.put("errorMessage", result.getErrorMessage());
            }
        }
        publish(payload);
    }

    // ==================== 预检 / 校验 / 删除 ====================

    /**
     * 恢复前预检 / 试运行（只读，不对目标库写数据）。
     *
     * @param params 预检参数
     * @return 预检结果
     */
    public RestorePreflight.PreflightResult preflight(PreflightParams params) {
        String[] conn = resolveConnection();
        DatabaseRestoreConfig config = DatabaseRestoreConfig.builder()
                .jdbcUrl(conn[0])
                .username(conn[1])
                .password(conn[2])
                .scriptFile(Paths.get(params.scriptFile()))
                .dryRun(params.dryRun())
                .verifyChecksum(params.verifyChecksum())
                .build();
        return params.dryRun()
                ? backupManager.dryRunRestore(config)
                : backupManager.preflightRestore(config);
    }

    /**
     * 离线校验备份产物完整性（按清单核对字节数与 SHA-256）。
     *
     * @param target 备份目录、.sql 脚本或 .zip 归档
     * @return 校验结果
     */
    public BackupVerifier.VerificationResult verify(String target) {
        Path path = Paths.get(target);
        if (!path.isAbsolute()) {
            // 与 deleteBackup 同口径：相对路径按备份根目录解析并做白名单校验（页面只传备份根内相对路径）
            Path root = backupDir().toAbsolutePath().normalize();
            path = root.resolve(target).normalize();
            if (!path.startsWith(root) || path.equals(root)) {
                throw new IllegalArgumentException("校验目标必须在备份目录内: " + target);
            }
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("备份不存在: " + target);
        }
        return backupManager.verifyBackup(path);
    }

    /**
     * 删除备份产物。
     *
     * <p>安全约束：目标必须位于备份根目录内；只清理 {@code database-*} 命名空间
     * （备份脚本、同名录与 manifest.json），不触碰其他文件；目录内产物清空后
     * 移除空目录。与 utils 保留策略口径一致。</p>
     *
     * @param target 相对备份根目录的路径（如 {@code 2026-11-24} 或
     *               {@code 2026-11-24/database-monitordb-xxx.sql}）
     * @return 删除的文件数
     */
    public int deleteBackup(String target) {
        Path root = backupDir().toAbsolutePath().normalize();
        Path path = root.resolve(target).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("删除目标必须在备份目录内: " + target);
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("备份不存在: " + target);
        }
        int removed = deleteAllowed(path);
        cleanupEmptyDirs(root, path);
        return removed;
    }

    /**
     * 受限删除：文件必须是 {@code database-*} 脚本或 {@code manifest.json}；
     * 目录递归按同规则清理，产物清空后才移除目录本身。
     *
     * @param path 删除目标
     * @return 删除的文件数
     */
    private int deleteAllowed(Path path) {
        if (Files.isRegularFile(path)) {
            return isDeletableBackupFile(path) && deleteQuietly(path) ? 1 : 0;
        }
        int removed = 0;
        for (Path child : listChildren(path)) {
            if (isDeletableBackupFile(child) || child.getFileName().toString().startsWith("database-")) {
                removed += deleteTree(child);
            } else if (Files.isDirectory(child)) {
                removed += deleteAllowed(child);
            }
        }
        if (removed > 0 && listChildren(path).isEmpty()) {
            deleteQuietly(path);
        }
        return removed;
    }

    /**
     * 递归删除整棵文件树（仅对已判定可删的 {@code database-*} 命名空间调用）。
     *
     * @param path 删除目标
     * @return 删除的文件数
     */
    private int deleteTree(Path path) {
        int removed = 0;
        if (Files.isDirectory(path)) {
            for (Path child : listChildren(path)) {
                removed += deleteTree(child);
            }
        }
        if (deleteQuietly(path)) {
            removed++;
        }
        return removed;
    }

    /**
     * 删除因清理而空掉的父级目录（直到备份根目录为止）。
     *
     * @param root 备份根目录
     * @param from 删除起点
     */
    private void cleanupEmptyDirs(Path root, Path from) {
        Path parent = from.getParent();
        while (parent != null && !parent.equals(root)) {
            if (!listChildren(parent).isEmpty() || !deleteQuietly(parent)) {
                break;
            }
            parent = parent.getParent();
        }
    }

    /**
     * 是否为允许删除的备份产物文件。
     *
     * @param path 文件路径
     * @return true 表示可删
     */
    private static boolean isDeletableBackupFile(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith("database-") || BackupManifest.MANIFEST_FILE_NAME.equals(name);
    }

    /**
     * 列出目录子项（失败返回空列表）。
     *
     * @param dir 目录
     * @return 子项列表
     */
    private List<Path> listChildren(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.toList();
        } catch (IOException e) {
            log.warn("[database-backup] 遍历目录失败: {}", dir);
            return List.of();
        }
    }

    /**
     * 安静删除（失败只告警）。
     *
     * @param path 删除目标
     * @return 是否删除成功
     */
    private boolean deleteQuietly(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[database-backup] 删除失败: {} ({})", path, e.getMessage());
            return false;
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 备份中心开关检查（关闭时拒绝触发备份/恢复）。
     */
    private void checkEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("备份中心未启用（plugin.monitor.database-backup.enabled=false）");
        }
    }

    /**
     * 发布任务已受理事件（前端立即点亮「进行中」）。
     *
     * @param type 任务类型
     */
    private void publishSubmitted(String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("phase", "SUBMITTED");
        payload.put("time", LocalDateTime.now().toString());
        publish(payload);
    }

    /**
     * 发布失败事件（兜底路径：构建配置失败、策略未回调等）。
     *
     * @param type    任务类型
     * @param message 错误信息
     */
    private void publishFailure(String type, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("phase", "FAILURE");
        payload.put("time", LocalDateTime.now().toString());
        payload.put("success", false);
        payload.put("errorMessage", message);
        publish(payload);
    }

    /**
     * 更新状态快照并广播 SSE（running 标志取自互斥锁实时值）。
     *
     * @param payload 事件载荷
     */
    private void publish(Map<String, Object> payload) {
        payload.put("running", running.get());
        currentStatus = Collections.unmodifiableMap(payload);
        eventStreamRegistry.broadcast(payload);
    }

    /**
     * 任务收尾：清除 running 标志（保留最后一次事件字段供前端查询终态）。
     */
    private void finishTask() {
        running.set(false);
        Map<String, Object> snapshot = new LinkedHashMap<>(currentStatus);
        snapshot.put("running", false);
        currentStatus = Collections.unmodifiableMap(snapshot);
    }

    /**
     * 解析备份根目录（配置为空时默认 {user.home}/monitor-backup/database），并确保存在。
     *
     * @return 备份根目录
     */
    private Path backupDir() {
        String configured = properties.getBackupDir();
        Path dir = (configured == null || configured.isBlank())
                ? Paths.get(System.getProperty("user.home"), "monitor-backup", "database")
                : Paths.get(configured);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("备份目录不可创建: " + dir + " (" + e.getMessage() + ")");
        }
        return dir;
    }

    /**
     * 解析「当前库」JDBC 连接信息。
     *
     * <p>优先级：Spring Boot {@link DataSourceProperties}（声明式配置，最准）
     * → HikariDataSource（实例字段）→ Environment 的 spring.datasource.*。
     * 三者均不可用时抛错，由调用方转成失败事件。</p>
     *
     * @return [jdbcUrl, username, password]
     */
    private String[] resolveConnection() {
        DataSourceProperties props = dataSourcePropertiesProvider.getIfAvailable();
        if (props != null) {
            try {
                String url = props.determineUrl();
                if (url != null && !url.isBlank()) {
                    return new String[]{url, props.determineUsername(), props.determinePassword()};
                }
            } catch (Exception e) {
                log.debug("[database-backup] DataSourceProperties 解析失败，尝试数据源实例: {}", e.getMessage());
            }
        }
        HikariDataSource hikari = unwrapHikari();
        if (hikari != null && hikari.getJdbcUrl() != null && !hikari.getJdbcUrl().isBlank()) {
            return new String[]{hikari.getJdbcUrl(), hikari.getUsername(), hikari.getPassword()};
        }
        String url = environment.getProperty("spring.datasource.url");
        if (url != null && !url.isBlank()) {
            return new String[]{url,
                    environment.getProperty("spring.datasource.username"),
                    environment.getProperty("spring.datasource.password")};
        }
        throw new IllegalStateException(
                "无法解析当前数据源连接信息（DataSourceProperties / HikariDataSource / spring.datasource.url 均不可用）");
    }

    /**
     * 从 Spring DataSource 提取 Hikari 实例（支持 unwrap）。
     *
     * @return Hikari 数据源；不是 Hikari 时返回 null
     */
    private HikariDataSource unwrapHikari() {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari;
        }
        if (dataSource != null) {
            try {
                return dataSource.unwrap(HikariDataSource.class);
            } catch (Exception ignored) {
                // 非 Hikari 连接池（Druid 等），交由后续解析路径处理
            }
        }
        return null;
    }

    /**
     * jdbcUrl 中的密码段脱敏（jdbc:xxx://user:password@host/db）。
     *
     * @param jdbcUrl 原始连接串
     * @return 脱敏后的连接串
     */
    private static String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        return jdbcUrl.replaceAll("://([^:@/]+):[^@/]+@", "://$1:****@");
    }

    /**
     * 从 jdbcUrl 提取库名（最后一节路径，去查询参数）。
     *
     * @param jdbcUrl 连接串
     * @return 库名
     */
    private static String extractDbName(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        try {
            String noParams = jdbcUrl.split("\\?", 2)[0];
            int slash = noParams.lastIndexOf('/');
            return slash >= 0 ? noParams.substring(slash + 1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 对象转字符串（null 安全）。
     *
     * @param value 任意对象
     * @return 字符串
     */
    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 恢复参数。
     *
     * @param scriptFile       待恢复的备份脚本/归档路径
     * @param dropBeforeCreate 恢复前对 CREATE TABLE 先执行 DROP TABLE IF EXISTS
     * @param continueOnError  单条语句失败是否继续
     * @param skipEmptyCheck   跳过「目标库非空拒绝恢复」检查
     * @param verifyChecksum   恢复前校验 manifest.json 的 SHA-256
     * @param confirm          显式确认（必须 true）
     */
    public record RestoreParams(String scriptFile,
                                boolean dropBeforeCreate,
                                boolean continueOnError,
                                boolean skipEmptyCheck,
                                boolean verifyChecksum,
                                boolean confirm) {
    }

    /**
     * 预检参数。
     *
     * @param scriptFile     待预检的备份脚本/归档路径
     * @param dryRun         true=试运行（事务内执行后回滚）；false=只读预检
     * @param verifyChecksum 预检时是否校验 SHA-256 清单
     */
    public record PreflightParams(String scriptFile,
                                  boolean dryRun,
                                  boolean verifyChecksum) {
    }
}
