package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.datasync.agent.support.source.JdbcDataSyncAgentSource;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.datasync.mapping.DataSyncFieldMapping;
import com.chua.starter.datasync.mapping.DefaultFieldMappingConverter;
import com.chua.starter.monitor.support.datasync.MonitorFileRowsSupport;
import com.chua.starter.monitor.support.datasync.MonitorHttpRowsSupport;
import com.chua.starter.monitor.support.datasync.MonitorJdbcSyncSink;
import com.chua.starter.monitor.support.datasync.SimpleFieldMapping;
import com.chua.starter.monitor.support.datasync.SyncDirWatcher;
import com.chua.starter.monitor.support.datasync.SyncHookNotify;
import com.chua.starter.monitor.support.datasync.SyncHttpOptions;
import com.chua.starter.monitor.support.datasync.SyncJdbcSupport;
import com.chua.starter.monitor.support.datasync.SyncMode;
import com.chua.starter.monitor.support.datasync.SyncSourceOptions;
import com.chua.starter.monitor.support.datasync.SyncSourceType;
import com.chua.starter.monitor.support.datasync.SyncTaskHookNotifier;
import com.chua.starter.monitor.support.datasync.SyncWriteMode;
import com.chua.starter.monitor.support.datasync.loader.JdbcCsvLoader;
import com.chua.starter.monitor.support.entity.MsSyncTask;
import com.chua.starter.monitor.support.entity.MsSyncTaskRunLog;
import com.chua.starter.monitor.support.mapper.MsSyncTaskMapper;
import com.chua.starter.monitor.support.mapper.MsSyncTaskRunLogMapper;
import com.chua.starter.monitor.support.model.SyncColumnVO;
import com.chua.starter.monitor.support.model.SyncFieldMappingVO;
import com.chua.starter.monitor.support.model.SyncPreviewVO;
import com.chua.starter.monitor.support.model.SyncRunLogStatsVO;
import com.chua.starter.monitor.support.model.SyncRunLogTrendVO;
import com.chua.starter.monitor.support.model.SyncTaskRunLogDetailVO;
import com.chua.starter.monitor.support.model.SyncTaskRunLogItemVO;
import com.chua.starter.monitor.support.model.SyncTaskRunLogVO;
import com.chua.starter.monitor.support.model.SyncTaskRunVO;
import com.chua.starter.monitor.support.model.BulkImportResultVO;
import com.chua.starter.monitor.support.model.BulkPreviewVO;
import com.chua.starter.monitor.support.model.dto.BulkImportDTO;
import com.chua.starter.monitor.support.model.dto.SyncPreviewDTO;
import com.chua.starter.monitor.support.model.dto.SyncTargetDTO;
import com.chua.starter.monitor.support.model.dto.SyncTaskDTO;
import com.chua.starter.monitor.support.model.vo.SyncTargetPlan;
import com.chua.starter.monitor.support.service.SyncTaskService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 数据同步任务服务实现。
 *
 * <p><b>与 datasync 的分工</b>：读用引擎的 {@link JdbcDataSyncAgentSource}，字段映射用引擎的
 * {@link DefaultFieldMappingConverter}（配 {@link SimpleFieldMapping}），写用实现引擎
 * {@code DataSyncAgentSink} 契约的 {@link MonitorJdbcSyncSink}。也就是「数据面」整体复用引擎，
 * 只是把「调度」换成 Spring 的 {@link ThreadPoolTaskScheduler}：</p>
 * <ul>
 *   <li>引擎的 {@code DefaultSyncDataSchedulerManager} 每秒轮询所有映射，但执行器按
 *       {@code outputId|sinkId} <b>只订阅一次</b>，任务被修改（换了目标表/列）后仍然会把数据
 *       投给旧的 Sink 实例，控制台无法安全地改任务；</li>
 *   <li>引擎的 {@code JdbcDataSyncAgentSink} 把写入异常吞在内部且不返回行数，
 *       控制台拿不到「写了多少行 / 为什么失败」。</li>
 * </ul>
 * <p>换成 Spring 定时器后，每次触发都是一次完整的「重建源与 Sink → 同步写入 → 回写状态」，
 * 任务可随时修改，且结果可信。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncTaskServiceImpl implements SyncTaskService {

    /**
     * 默认批量提交条数
     */
    private static final int DEFAULT_BATCH_SIZE = 500;

    /**
     * 执行开始时刻格式（与 {@code SyncTaskRunLogVO} 的 runTime 一致，供列表/详情展示）
     */
    private static final DateTimeFormatter RUN_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 结果说明字段的最大长度（与 ms_sync_task_last_message 的 512 留出余量）
     */
    private static final int MESSAGE_MAX_LENGTH = 500;

    /**
     * 增量字段名白名单：只允许字母、数字、下划线（直接拼进 SQL，必须收紧防注入）
     */
    private static final Pattern INC_FIELD_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    /**
     * 纯数字水位（整数/小数）：字面量不加引号，让数据库按数值比较
     */
    private static final Pattern NUMERIC_SEQ_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    /**
     * 任务映射器
     */
    private final MsSyncTaskMapper taskMapper;

    /**
     * 执行日志映射器（「同步日志」菜单页的全局分页与详情查询）
     */
    private final MsSyncTaskRunLogMapper runLogMapper;

    /**
     * 数据源中心服务：取 JDBC 连接三元组与元数据连接
     */
    private final DatasourceService datasourceService;

    /**
     * JSON 读写（字段映射）
     */
    private final ObjectMapper objectMapper;

    /**
     * 字段映射转换器（引擎实现）
     */
    private final DefaultFieldMappingConverter fieldMappingConverter = new DefaultFieldMappingConverter();

    /**
     * 定时调度器
     */
    private ThreadPoolTaskScheduler scheduler;

    /**
     * 任务 ID 到已排定定时的映射
     */
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 正在执行的任务标记，避免手动执行与定时触发叠加跑同一任务
     */
    private final Map<Long, AtomicBoolean> runningTasks = new ConcurrentHashMap<>();

    /**
     * 目录源任务的目录监听器（任务 ID → 监听）
     */
    private final Map<Long, SyncDirWatcher> dirWatchers = new ConcurrentHashMap<>();

    /**
     * 执行调试日志缓冲（内存态，供同步页「日志」弹窗逐步排障）
     */
    private final SyncTaskRunLogBuffer runLogBuffer;

    /**
     * Hook 通知器：执行结果 / 每条数据的 HTTP 回调（旁路，失败不影响同步）
     */
    private final SyncTaskHookNotifier hookNotifier;

    /**
     * 启动时恢复定时任务。
     *
     * <p>建表由 Flyway 在上下文启动前完成；这里仍做兜底：表还没建好时只记警告，
     * 不让整个应用因为「定时任务表不存在」而起不来。</p>
     */
    @PostConstruct
    public void initScheduler() {
        addMissingSourceColumns();
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("sync-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        try {
            List<MsSyncTask> tasks = taskMapper.selectList(
                    new LambdaQueryWrapper<MsSyncTask>().eq(MsSyncTask::getSyncTaskEnabled, 1));
            for (MsSyncTask task : tasks) {
                schedule(task);
            }
            log.info("[sync-task] 启动完成，已恢复定时任务 {} 个", scheduledTasks.size());
        } catch (Exception e) {
            log.warn("[sync-task] 恢复定时任务失败（同步任务表可能尚未建立）: {}", e.getMessage());
        }
        scheduleLogPurge();
    }

    /**
     * 每天清理一次过期的执行日志（保留 30 天）。
     *
     * <p>用调度器自身的 cron 而不是再引一个 {@code @Scheduled}：本模块的调度统一
     * 由这里持有的 {@link ThreadPoolTaskScheduler} 驱动，多一处调度入口反而难排查。</p>
     */
    private void scheduleLogPurge() {
        try {
            scheduler.schedule(
                    () -> runLogBuffer.purgeExpired(),
                    new org.springframework.scheduling.support.CronTrigger("0 30 3 * * ?"));
        } catch (Exception e) {
            log.warn("[sync-log] 注册执行日志清理任务失败: {}", e.getMessage());
        }
    }

    /**
     * 输入源扩展列（旧库缺失时补齐）。
     *
     * <p>表名与列名均为本类固定注册项，不接收外部标识符；值定义不属于 DDL 参数绑定范围。</p>
     */
    private static final Map<String, String> SOURCE_MISSING_COLUMNS = Map.of(
            "ms_sync_task_source_type", "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_source_type VARCHAR(16) NOT NULL DEFAULT 'JDBC' COMMENT '输入源类型 JDBC/FILE/DIR/HTTP_JSON/HTTP_HTML'",
            "ms_sync_task_source_path", "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_source_path VARCHAR(512) DEFAULT NULL COMMENT '源定位：FILE=本地文件绝对路径 / DIR=本地目录绝对路径 / HTTP_*=http(s) 请求地址'",
            "ms_sync_task_source_options", "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_source_options TEXT DEFAULT NULL COMMENT '输入源扩展选项 JSON'");

    /**
     * 旧库里存在但被建成 NOT NULL 的列（应为可空）。
     *
     * <p>FILE / DIR / HTTP 三种输入源<b>没有</b>源数据源，也没有源 SQL，旧库把这两列建成
     * NOT NULL，于是插这类任务报 {@code Column 'ms_sync_task_source_datasource_id' cannot be null}。</p>
     *
     * <p>表名与列名均为本类固定注册项，不接收外部标识符。</p>
     */
    private static final Map<String, String> SOURCE_NULLABLE_COLUMNS = Map.of(
            "ms_sync_task_source_datasource_id",
            "ALTER TABLE ms_sync_task MODIFY COLUMN ms_sync_task_source_datasource_id BIGINT DEFAULT NULL COMMENT '源数据源 ID(ms_datasource.id)，仅 JDBC 源使用'",
            "ms_sync_task_source_sql",
            "ALTER TABLE ms_sync_task MODIFY COLUMN ms_sync_task_source_sql TEXT DEFAULT NULL COMMENT '源读取 SQL，必须是 SELECT，仅 JDBC 源使用'");

    /**
     * 同步模式 / 增量位点 / Hook 通知列（旧库缺失时补齐）。
     *
     * <p>与 {@link #SOURCE_MISSING_COLUMNS} 同一套自愈机制：建表脚本用 CREATE TABLE IF NOT EXISTS，
     * 对已存在的表不补列，旧库必须靠这里对齐。表名与列名均为本类固定注册项，不接收外部标识符。</p>
     */
    private static final Map<String, String> MODE_HOOK_MISSING_COLUMNS = Map.of(
            "ms_sync_task_sync_mode",
            "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_sync_mode VARCHAR(16) NOT NULL DEFAULT 'FULL' COMMENT '同步模式 FULL 全量/INCREMENTAL 增量'",
            "ms_sync_task_inc_field",
            "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_inc_field VARCHAR(255) DEFAULT NULL COMMENT '增量字段（仅增量模式），多字段按顺序逗号分隔'",
            "ms_sync_task_inc_batch_size",
            "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_inc_batch_size INT DEFAULT NULL COMMENT '增量每批数量：单次执行最多同步条数，空表示不限'",
            "ms_sync_task_last_seq",
            "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_last_seq VARCHAR(255) DEFAULT NULL COMMENT '增量水位（服务端执行成功后回写）'",
            "ms_sync_task_hook_url",
            "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_hook_url VARCHAR(512) DEFAULT NULL COMMENT 'Hook 通知地址（http/https），空表示不通知'",
            "ms_sync_task_hook_notify",
            "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_hook_notify VARCHAR(16) DEFAULT NULL COMMENT 'Hook 通知时机 DONE/ROW，全量固定 DONE'",
            "ms_sync_task_targets",
            "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_targets MEDIUMTEXT DEFAULT NULL COMMENT '多目标 JSON 数组，空表示按旧单目标字段解析'",
            "ms_sync_task_target_seqs",
            "ALTER TABLE ms_sync_task ADD COLUMN ms_sync_task_target_seqs TEXT DEFAULT NULL COMMENT '多目标水位 JSON，服务端回写'");

    /**
     * 把旧库的 {@code ms_sync_task} 结构对齐到建表脚本：补齐缺失列 + 放宽应为可空的列。
     *
     * <p><b>为什么必须在这里做</b>：建表语句用的是 {@code CREATE TABLE IF NOT EXISTS}，
     * 对<b>已存在</b>的表既不补列也不改约束。旧库实测有三处漂移，缺一不可：</p>
     * <ol>
     *   <li>缺 {@code source_type / source_path / source_options} 三列 ——
     *       {@code taskMapper.insert} 一律报
     *       {@code Unknown column 'ms_sync_task_source_type'}，<b>任何同步任务都建不出来</b>，
     *       连只需 source_type 取默认值的 JDBC 任务也建不了；</li>
     *   <li>{@code source_datasource_id} 与 {@code source_sql} 被建成 NOT NULL ——
     *       FILE / DIR / HTTP 源天然没有这两项，插入必报 cannot be null；</li>
     *   <li>{@code initScheduler} 恢复定时那段把异常吞成一条 warn，启动日志完全看不出问题，
     *       功能却是全废的。</li>
     * </ol>
     *
     * <p>做法与数据源中心给 {@code sys_setting} 补列一致：读表结构 → 缺则 ADD / 约束不符则 MODIFY
     * → 单项失败不阻断启动。</p>
     */
    private void addMissingSourceColumns() {
        // connection(null) 取的是平台默认数据源 —— 要改的正是平台自己库里 ms_sync_task 的结构，
        // 不能借数据源中心里配置的业务数据源（那些连的是外部库，改了也没用）
        try (Connection connection = datasourceService.connection(null)) {
            Map<String, Boolean> columns = columnNullability(connection, "ms_sync_task");
            if (columns.isEmpty()) {
                log.warn("[sync-task] ms_sync_task 尚不存在或读不到列，跳过结构对齐");
                return;
            }
            try (java.sql.Statement statement = connection.createStatement()) {
                for (Map.Entry<String, String> entry : SOURCE_MISSING_COLUMNS.entrySet()) {
                    if (columns.containsKey(entry.getKey())) {
                        continue;
                    }
                    try {
                        statement.execute(entry.getValue());
                        log.info("[sync-task] 已补齐表 ms_sync_task 缺失列 {}", entry.getKey());
                    } catch (java.sql.SQLException e) {
                        log.warn("[sync-task] 补齐列 {} 失败: {}", entry.getKey(), e.getMessage());
                    }
                }
                for (Map.Entry<String, String> entry : MODE_HOOK_MISSING_COLUMNS.entrySet()) {
                    if (columns.containsKey(entry.getKey())) {
                        continue;
                    }
                    try {
                        statement.execute(entry.getValue());
                        log.info("[sync-task] 已补齐表 ms_sync_task 缺失列 {}", entry.getKey());
                    } catch (java.sql.SQLException e) {
                        log.warn("[sync-task] 补齐列 {} 失败: {}", entry.getKey(), e.getMessage());
                    }
                }
                for (Map.Entry<String, String> entry : SOURCE_NULLABLE_COLUMNS.entrySet()) {
                    Boolean nullable = columns.get(entry.getKey());
                    if (nullable == null || nullable) {
                        continue;
                    }
                    try {
                        statement.execute(entry.getValue());
                        log.info("[sync-task] 已把表 ms_sync_task 的 {} 放宽为可空", entry.getKey());
                    } catch (java.sql.SQLException e) {
                        log.warn("[sync-task] 放宽列 {} 为可空失败: {}", entry.getKey(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[sync-task] 对齐 ms_sync_task 表结构失败: {}", e.getMessage());
        }
    }

    /**
     * 读取表的列名与是否可空。
     *
     * @param connection 数据库连接
     * @param tableName  表名
     * @return 列名（小写）到可空标记的映射；读不到返回空映射
     */
    private Map<String, Boolean> columnNullability(Connection connection, String tableName) {
        Map<String, Boolean> columns = new LinkedHashMap<>();
        try (java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet rs = statement.executeQuery(
                     "SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "'")) {
            while (rs.next()) {
                columns.put(rs.getString(1).toLowerCase(Locale.ROOT), "YES".equalsIgnoreCase(rs.getString(2)));
            }
        } catch (Exception e) {
            log.warn("[sync-task] 读取 {} 列结构失败: {}", tableName, e.getMessage());
        }
        return columns;
    }

    /**
     * 停机时取消全部定时并关闭调度器。
     */
    @PreDestroy
    public void destroyScheduler() {
        scheduledTasks.keySet().forEach(this::cancelSchedule);
        // 目录源的监听不在 scheduledTasks 里（没有 cron 也可能监听），单独收一遍
        new ArrayList<>(dirWatchers.keySet()).forEach(this::stopWatch);
        if (scheduler != null) {
            scheduler.shutdown();
        }
        log.info("[sync-task] 调度器与目录监听已关闭");
    }

    @Override
    public ReturnResult<List<MsSyncTask>> list() {
        try {
            List<MsSyncTask> tasks = taskMapper.selectList(
                    new LambdaQueryWrapper<MsSyncTask>().orderByAsc(MsSyncTask::getId));
            return ReturnResult.ok(tasks);
        } catch (Exception e) {
            log.error("[sync-task] 查询任务列表失败", e);
            return ReturnResult.error("查询同步任务失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<MsSyncTask> get(Long id) {
        try {
            MsSyncTask task = requireTask(id);
            return ReturnResult.ok(task);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<MsSyncTask> create(SyncTaskDTO request) {
        try {
            MsSyncTask task = new MsSyncTask();
            applyRequest(task, request);
            taskMapper.insert(task);
            schedule(task);
            return ReturnResult.ok(taskMapper.selectById(task.getId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-task] 新建同步任务失败", e);
            return ReturnResult.error("新建同步任务失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<MsSyncTask> update(Long id, SyncTaskDTO request) {
        try {
            MsSyncTask task = requireTask(id);
            applyRequest(task, request);
            taskMapper.updateById(task);
            schedule(task);
            return ReturnResult.ok(taskMapper.selectById(id));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-task] 修改同步任务失败, id={}", id, e);
            return ReturnResult.error("修改同步任务失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> delete(Long id) {
        try {
            requireTask(id);
            cancelSchedule(id);
            taskMapper.deleteById(id);
            runLogBuffer.remove(id);
            return ReturnResult.ok(Boolean.TRUE);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-task] 删除同步任务失败, id={}", id, e);
            return ReturnResult.error("删除同步任务失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> setEnabled(Long id, boolean enabled) {
        try {
            MsSyncTask task = requireTask(id);
            var update = new LambdaUpdateWrapper<MsSyncTask>()
                    .eq(MsSyncTask::getId, id)
                    .set(MsSyncTask::getSyncTaskEnabled, enabled ? 1 : 0);
            taskMapper.update(null, update);
            task.setSyncTaskEnabled(enabled ? 1 : 0);
            schedule(task);
            return ReturnResult.ok(Boolean.TRUE);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-task] 切换启用状态失败, id={}", id, e);
            return ReturnResult.error("切换同步任务状态失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SyncTaskRunVO> runOnce(Long id) {
        try {
            MsSyncTask task = requireTask(id);
            return ReturnResult.ok(execute(task, SyncTaskRunLogVO.TRIGGER_MANUAL));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-task] 执行同步任务失败, id={}", id, e);
            return ReturnResult.error("执行同步任务失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<SyncColumnVO>> validateSource(SyncPreviewDTO request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("校验参数不能为空");
            }
            List<SyncColumnVO> columns = resolvePreviewSourceColumns(request, true);
            return ReturnResult.ok(columns);
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            // 与 preview() 的专用 catch 同口径：不加「预览字段映射失败」前缀，
            // 前端可直接把这句话显示在源 SQL 输入框下方。
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-task] 源校验失败", e);
            return ReturnResult.error("源读取失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SyncPreviewVO> preview(SyncPreviewDTO request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("预览参数不能为空");
            }
            List<SyncColumnVO> sourceColumns = resolvePreviewSourceColumns(request, false);
            List<SyncColumnVO> targetColumns = loadTargetColumns(request.targetDatasourceId(), request.targetTable());
            List<SyncFieldMappingVO> mapping = request.mapping();
            boolean configured = mapping != null && !mapping.isEmpty();
            if (!configured) {
                mapping = autoMatch(sourceColumns, targetColumns);
            }
            List<String> warnings = collectWarnings(sourceColumns, targetColumns, mapping, configured);
            return ReturnResult.ok(new SyncPreviewVO(sourceColumns, targetColumns, mapping, warnings));
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-task] 预览字段映射失败", e);
            return ReturnResult.error("预览字段映射失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<BulkImportResultVO> bulkImport(BulkImportDTO request) {
        long startNanos = System.nanoTime();
        try {
            if (request == null) {
                throw new IllegalArgumentException("批量导入参数不能为空");
            }
            if (request.getFilePath() == null || request.getFilePath().isBlank()) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            Path file = Path.of(request.getFilePath().trim());
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("文件不存在或不是普通文件: " + file);
            }
            List<BulkImportDTO.BulkImportOutputDTO> outputs = request.getOutputs();
            if (outputs == null || outputs.isEmpty()) {
                throw new IllegalArgumentException("至少需要配置一个输出目标");
            }
            // 源文件统一读成行流（Map<列名, 值>），与 datasync 文件源同源，一次读、多目标复用
            SyncSourceOptions sourceOptions = SyncSourceOptions.defaults();
            if (request.getDelimiter() != null && !request.getDelimiter().isBlank()) {
                sourceOptions = new SyncSourceOptions("CSV", request.getDelimiter(), 0, null, null, null);
            }
            List<Map<String, Object>> rows = MonitorFileRowsSupport.read(file, sourceOptions)
                    .collectList().block();
            List<Map<String, Object>> dataRows = rows == null ? List.of() : rows;
            List<String> fileHeaders = readFileHeaders(file, request, sourceOptions);
            long readCount = dataRows.size();

            List<BulkImportResultVO.BulkImportOutputResultVO> outputResults = new ArrayList<>(outputs.size());
            long writeTotal = 0L;
            int failedCount = 0;
            for (BulkImportDTO.BulkImportOutputDTO output : outputs) {
                long targetStart = System.nanoTime();
                BulkImportResultVO.BulkImportOutputResultVO out = new BulkImportResultVO.BulkImportOutputResultVO();
                out.setTargetDatasourceId(output.getTargetDatasourceId());
                out.setTargetTable(output.getTargetTable());
                out.setReadCount(readCount);
                try {
                    validateDatasource(output.getTargetDatasourceId(), "目标数据源");
                    DatasourceJdbcInfo target = datasourceService.jdbcInfo(output.getTargetDatasourceId());
                    JdbcCsvLoader loader = csvLoaderFor(target);
                    if (loader == null) {
                        throw new UnsupportedOperationException("目标库方言不支持 CSV 快速导入（当前支持 MySQL / PostgreSQL）");
                    }
                    List<String> targetColumns = outputColumns(output, fileHeaders);
                    if (targetColumns.isEmpty()) {
                        throw new IllegalArgumentException("字段映射为空，无法确定目标列");
                    }
                    // 规范 CSV 临时文件：表头 = 目标列顺序，行值按映射取源列
                    Path csvFile = Files.createTempFile("bulk-import-", ".csv");
                    try {
                        long written = writeMappedCsv(csvFile, dataRows, output.getColumnMapping(), targetColumns);
                        long loaded = loader.load(target, output.getTargetTable(), targetColumns, csvFile, encodingOf(request));
                        out.setWriteCount(loaded > 0 ? loaded : written);
                        out.setStatus("SUCCESS");
                        out.setCostMs(costMs(targetStart));
                        out.setMessage("快速导入成功，写入 " + out.getWriteCount() + " 行");
                    } finally {
                        Files.deleteIfExists(csvFile);
                    }
                } catch (Exception e) {
                    failedCount++;
                    out.setStatus("FAILED");
                    out.setCostMs(costMs(targetStart));
                    out.setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : rootMessage(e));
                }
                writeTotal += out.getWriteCount();
                outputResults.add(out);
            }
            boolean allOk = failedCount == 0;
            BulkImportResultVO result = new BulkImportResultVO();
            result.setStatus(allOk ? "SUCCESS" : "FAILED");
            result.setReadCount(readCount);
            result.setWriteCount(writeTotal);
            result.setCostMs(costMs(startNanos));
            result.setMessage(allOk
                    ? "全部目标快速导入完成"
                    : (failedCount + "/" + outputs.size() + " 个目标导入失败，详见各目标结果"));
            result.setOutputs(outputResults);
            return ReturnResult.ok(result);
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[bulk-import] 批量导入失败", e);
            return ReturnResult.error("批量导入失败: " + e.getMessage());
        }
    }

    /**
     * 读取源文件表头（csv 读首行，excel 读首个工作表首行）。
     *
     * @param file          源文件
     * @param request       批量导入入参
     * @param sourceOptions 源选项
     * @return 列名列表
     */
    private List<String> readFileHeaders(Path file, BulkImportDTO request, SyncSourceOptions sourceOptions) {
        if (!request.isHasHeader()) {
            return List.of();
        }
        try {
            return MonitorFileRowsSupport.headers(file, sourceOptions);
        } catch (Exception e) {
            log.warn("[bulk-import] 读取表头失败，按无表头处理: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 按方言取 CSV 快速导入 SPI 实现。
     *
     * @param target 目标库连接信息
     * @return SPI 实现；方言不支持时返回 null
     */
    private JdbcCsvLoader csvLoaderFor(DatasourceJdbcInfo target) {
        if (target == null || target.url() == null) {
            return null;
        }
        String url = target.url().toLowerCase(Locale.ROOT);
        String dialect = url.startsWith("jdbc:mysql:") ? "mysql"
                : url.startsWith("jdbc:postgresql:") ? "postgresql" : null;
        if (dialect == null) {
            return null;
        }
        try {
            return ServiceProvider.of(JdbcCsvLoader.class).getExtension(dialect);
        } catch (Exception e) {
            log.warn("[bulk-import] 未找到方言 {} 的 CSV 快速导入实现: {}", dialect, e.getMessage());
            return null;
        }
    }

    /**
     * 推导目标列清单（与 CSV 表头顺序一致）。
     *
     * <p>优先按字段映射的目标列顺序；无映射时退回源表头（同名导入）。</p>
     *
     * @param output      输出目标
     * @param fileHeaders 源表头
     * @return 目标列名清单
     */
    private List<String> outputColumns(BulkImportDTO.BulkImportOutputDTO output, List<String> fileHeaders) {
        List<BulkImportDTO.BulkImportMappingDTO> mapping = output.getColumnMapping();
        if (mapping != null && !mapping.isEmpty()) {
            List<String> columns = new ArrayList<>(mapping.size());
            for (BulkImportDTO.BulkImportMappingDTO m : mapping) {
                if (m.getTarget() != null && !m.getTarget().isBlank()) {
                    columns.add(m.getTarget().trim());
                }
            }
            return columns;
        }
        return fileHeaders;
    }

    /**
     * 把源数据行按映射写成本目标所需的规范 CSV（表头 = 目标列顺序）。
     *
     * @param csvFile      临时 CSV 文件
     * @param dataRows     源数据行
     * @param mapping      字段映射（可为空 = 同名）
     * @param targetColumns 目标列清单
     * @return 写入的数据行数
     * @throws Exception 写出失败
     */
    private long writeMappedCsv(Path csvFile, List<Map<String, Object>> dataRows,
                                List<BulkImportDTO.BulkImportMappingDTO> mapping,
                                List<String> targetColumns) throws Exception {
        // 目标列 → 源列 的取值映射
        Map<String, String> sourceOf = new LinkedHashMap<>();
        if (mapping != null) {
            for (BulkImportDTO.BulkImportMappingDTO m : mapping) {
                if (m.getSource() != null && m.getTarget() != null && !m.getTarget().isBlank()) {
                    sourceOf.put(m.getTarget().trim(), m.getSource().trim());
                }
            }
        }
        try (var writer = Files.newBufferedWriter(csvFile, StandardCharsets.UTF_8)) {
            writer.write(String.join(",", targetColumns));
            writer.write("\n");
            long count = 0L;
            for (Map<String, Object> row : dataRows) {
                List<String> cells = new ArrayList<>(targetColumns.size());
                for (String targetColumn : targetColumns) {
                    String sourceKey = sourceOf.containsKey(targetColumn) ? sourceOf.get(targetColumn) : targetColumn;
                    Object value = sourceKey == null ? null : row.get(sourceKey);
                    cells.add(csvEscape(value == null ? "" : String.valueOf(value)));
                }
                writer.write(String.join(",", cells));
                writer.write("\n");
                count++;
            }
            return count;
        }
    }

    /**
     * CSV 单元格转义：含逗号/引号/换行的值用双引号包裹，内部引号翻倍。
     *
     * @param value 原始值
     * @return 转义后的单元格
     */
    private static String csvEscape(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 目标文件字符集（默认 UTF-8）。
     *
     * @param request 批量导入入参
     * @return 字符集名
     */
    private String encodingOf(BulkImportDTO request) {
        return request.getEncoding() == null || request.getEncoding().isBlank() ? "UTF-8" : request.getEncoding().trim();
    }

    @Override
    public ReturnResult<BulkPreviewVO> bulkPreview(BulkImportDTO request) {
        try {
            if (request == null || request.getFilePath() == null || request.getFilePath().isBlank()) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            Path file = Path.of(request.getFilePath().trim());
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("文件不存在或不是普通文件: " + file);
            }
            SyncSourceOptions sourceOptions = SyncSourceOptions.defaults();
            if (request.getDelimiter() != null && !request.getDelimiter().isBlank()) {
                sourceOptions = new SyncSourceOptions("CSV", request.getDelimiter(), 0, null, null, null);
            }
            List<String> headers = request.isHasHeader() ? readFileHeaders(file, request, sourceOptions) : List.of();
            List<Map<String, Object>> rows = MonitorFileRowsSupport.read(file, sourceOptions)
                    .collectList().block();
            List<Map<String, Object>> all = rows == null ? List.of() : rows;
            // 只回前 20 行做预览，避免大文件整份下发
            int previewLimit = Math.min(20, all.size());
            List<Map<String, String>> previewRows = new ArrayList<>(previewLimit);
            for (int i = 0; i < previewLimit; i++) {
                Map<String, String> row = new LinkedHashMap<>();
                all.get(i).forEach((k, v) -> row.put(k, v == null ? "" : String.valueOf(v)));
                previewRows.add(row);
            }
            BulkPreviewVO vo = new BulkPreviewVO();
            vo.setHeaders(headers);
            vo.setRows(previewRows);
            vo.setTotal(all.size());
            return ReturnResult.ok(vo);
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[bulk-import] 文件预览失败", e);
            return ReturnResult.error("文件预览失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<SyncTaskRunLogVO>> runLogs(Long id) {
        try {
            requireTask(id);
            return ReturnResult.ok(runLogBuffer.list(id));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-task] 查询执行日志失败, id={}", id, e);
            return ReturnResult.error("查询执行日志失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<IPage<SyncTaskRunLogItemVO>> runLogPage(long page, long size, Long taskId, String status, String trigger, String keyword) {
        try {
            long safePage = Math.max(page, 1);
            long safeSize = Math.min(Math.max(size, 1), 500);
            LambdaQueryWrapper<MsSyncTaskRunLog> wrapper = new LambdaQueryWrapper<MsSyncTaskRunLog>()
                    .eq(taskId != null, MsSyncTaskRunLog::getRunLogTaskId, taskId)
                    .eq(hasText(status), MsSyncTaskRunLog::getRunLogStatus, status)
                    .eq(hasText(trigger), MsSyncTaskRunLog::getRunLogTrigger, trigger)
                    .like(hasText(keyword), MsSyncTaskRunLog::getRunLogLines, keyword == null ? "" : keyword.trim())
                    .orderByDesc(MsSyncTaskRunLog::getRunLogRunTime, MsSyncTaskRunLog::getId);
            // 列表不加载 LONGTEXT 正文（行数已单独落库，点开详情再整体读回）
            wrapper.select(MsSyncTaskRunLog::getId, MsSyncTaskRunLog::getRunLogTaskId,
                    MsSyncTaskRunLog::getRunLogTrigger, MsSyncTaskRunLog::getRunLogStatus,
                    MsSyncTaskRunLog::getRunLogRunTime, MsSyncTaskRunLog::getRunLogCostMs,
                    MsSyncTaskRunLog::getRunLogLineCount,
                    MsSyncTaskRunLog::getRunLogReadCount, MsSyncTaskRunLog::getRunLogWriteCount);
            IPage<MsSyncTaskRunLog> rows = runLogMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
            // 批量取任务名：日志行可能引用已删除的任务，按 ID 存在性取，缺失回退 "（任务已删除）"
            Map<Long, String> nameById = new HashMap<>();
            List<Long> taskIds = rows.getRecords().stream()
                    .map(MsSyncTaskRunLog::getRunLogTaskId)
                    .filter(Objects::nonNull)
                    .distinct().toList();
            if (!taskIds.isEmpty()) {
                for (MsSyncTask t : taskMapper.selectBatchIds(taskIds)) {
                    nameById.put(t.getId(), t.getSyncTaskName());
                }
            }
            List<SyncTaskRunLogItemVO> items = rows.getRecords().stream().map(r -> new SyncTaskRunLogItemVO(
                    r.getId(),
                    r.getRunLogTaskId(),
                    nameById.getOrDefault(r.getRunLogTaskId(), "（任务已删除）"),
                    r.getRunLogTrigger(),
                    r.getRunLogStatus(),
                    r.getRunLogRunTime() == null ? "" : r.getRunLogRunTime().format(RUN_TIME_FORMAT),
                    r.getRunLogCostMs(),
                    r.getRunLogLineCount(),
                    r.getRunLogReadCount(),
                    r.getRunLogWriteCount())).toList();
            Page<SyncTaskRunLogItemVO> out = new Page<>(safePage, safeSize, rows.getTotal());
            out.setRecords(items);
            return ReturnResult.ok(out);
        } catch (Exception e) {
            log.error("[sync-log] 分页查询执行日志失败", e);
            return ReturnResult.error("查询执行日志失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SyncTaskRunLogDetailVO> runLogDetail(Long logId) {
        try {
            MsSyncTaskRunLog row = runLogMapper.selectById(logId);
            if (row == null) {
                return ReturnResult.error("日志不存在（可能已被 30 天保留策略清理）");
            }
            String taskName = null;
            if (row.getRunLogTaskId() != null) {
                MsSyncTask task = taskMapper.selectById(row.getRunLogTaskId());
                taskName = task == null ? "（任务已删除）" : task.getSyncTaskName();
            }
            List<SyncTaskRunLogVO.Line> lines = runLogBuffer.parseLines(row.getRunLogLines());
            Long readCount = row.getRunLogReadCount();
            Long writeCount = row.getRunLogWriteCount();
            // 存量行（V4.0.0.45 建列前）没有读/写列：按正文动态解析兜底，保证旧记录详情仍可见
            if ((readCount == null || writeCount == null) && !lines.isEmpty()) {
                long[] rw = SyncTaskRunLogBuffer.extractReadWrite(lines);
                readCount = readCount != null ? readCount : (rw[0] < 0 ? null : rw[0]);
                writeCount = writeCount != null ? writeCount : (rw[1] < 0 ? null : rw[1]);
            }
            return ReturnResult.ok(new SyncTaskRunLogDetailVO(
                    row.getId(),
                    row.getRunLogTaskId(),
                    taskName,
                    row.getRunLogTrigger(),
                    row.getRunLogStatus(),
                    row.getRunLogRunTime() == null ? "" : row.getRunLogRunTime().format(RUN_TIME_FORMAT),
                    row.getRunLogCostMs(),
                    row.getRunLogLineCount(),
                    lines,
                    readCount,
                    writeCount));
        } catch (Exception e) {
            log.error("[sync-log] 查询执行日志详情失败, id={}", logId, e);
            return ReturnResult.error("查询执行日志详情失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SyncRunLogStatsVO> runLogStats() {
        try {
            List<MsSyncTaskRunLog> rows = runLogMapper.selectList(
                    new LambdaQueryWrapper<MsSyncTaskRunLog>()
                            .select(MsSyncTaskRunLog::getRunLogStatus, MsSyncTaskRunLog::getRunLogCostMs,
                                    MsSyncTaskRunLog::getRunLogRunTime, MsSyncTaskRunLog::getRunLogLines,
                                    MsSyncTaskRunLog::getRunLogTaskId)
                            .ge(MsSyncTaskRunLog::getRunLogRunTime,
                                    LocalDateTime.now().minusDays(30)));
            if (rows == null || rows.isEmpty()) {
                return ReturnResult.ok(new SyncRunLogStatsVO(0, 0, 0, 0, 0, List.of(), List.of()));
            }
            long total = rows.size();
            long success = rows.stream().filter(r -> "SUCCESS".equals(r.getRunLogStatus())).count();
            long failed = total - success;
            double rate = total == 0 ? 0 : Math.round(success * 1000.0 / total) / 10.0;
            long sumCost = rows.stream().filter(r -> r.getRunLogCostMs() != null)
                    .mapToLong(MsSyncTaskRunLog::getRunLogCostMs).sum();
            long costCount = rows.stream().filter(r -> r.getRunLogCostMs() != null).count();
            long avgCost = costCount == 0 ? 0 : sumCost / costCount;

            // 近 24 小时逐小时分布（新在前，最多 24 个）
            List<SyncRunLogStatsVO.Hourly> hourly = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
            Map<String, long[]> byHour = new LinkedHashMap<>();
            for (int i = 23; i >= 0; i--) {
                byHour.put(now.minusHours(i).format(HOUR_KEY), new long[3]);
            }
            for (MsSyncTaskRunLog r : rows) {
                if (r.getRunLogRunTime() == null) {
                    continue;
                }
                long[] bucket = byHour.get(r.getRunLogRunTime().withMinute(0).withSecond(0).withNano(0).format(HOUR_KEY));
                if (bucket == null) {
                    continue;
                }
                bucket[0]++;
                if ("SUCCESS".equals(r.getRunLogStatus())) {
                    bucket[1]++;
                } else {
                    bucket[2]++;
                }
            }
            byHour.forEach((k, v) -> hourly.add(new SyncRunLogStatsVO.Hourly(k, v[0], v[1], v[2])));

            // 失败原因归类：失败日志的 ERROR 行按稳定关键段分组
            List<SyncRunLogStatsVO.FailureGroup> failures = new ArrayList<>();
            Map<String, Long> failCount = new LinkedHashMap<>();
            for (MsSyncTaskRunLog r : rows) {
                if (!"FAILED".equals(r.getRunLogStatus())) {
                    continue;
                }
                String label = classifyFailure(r.getRunLogLines());
                if (label != null) {
                    failCount.merge(label, 1L, Long::sum);
                }
            }
            failCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(8)
                    .forEach(e -> failures.add(new SyncRunLogStatsVO.FailureGroup(e.getKey(), e.getValue())));

            return ReturnResult.ok(new SyncRunLogStatsVO(total, success, failed, rate, avgCost, hourly, failures));
        } catch (Exception e) {
            log.error("[sync-log] 查询执行日志统计失败", e);
            return ReturnResult.error("查询执行日志统计失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SyncRunLogTrendVO> runLogTrend(Long taskId, Integer limit) {
        try {
            if (taskId == null) {
                throw new IllegalArgumentException("任务 ID 不能为空");
            }
            int safeLimit = Math.min(Math.max(limit == null ? 30 : limit, 1), 100);
            MsSyncTask task = taskMapper.selectById(taskId);
            String taskName = task == null ? "（任务已删除）" : task.getSyncTaskName();
            // 取「最近 safeLimit 次」：先按时间倒序截断，再反转为升序（新的在末尾）
            List<MsSyncTaskRunLog> rows = runLogMapper.selectList(
                    new LambdaQueryWrapper<MsSyncTaskRunLog>()
                            .eq(MsSyncTaskRunLog::getRunLogTaskId, taskId)
                            .orderByDesc(MsSyncTaskRunLog::getRunLogRunTime, MsSyncTaskRunLog::getId)
                            .last("LIMIT " + safeLimit));
            java.util.Collections.reverse(rows);
            List<SyncRunLogTrendVO.Point> points = new ArrayList<>();
            if (rows != null) {
                for (MsSyncTaskRunLog r : rows) {
                    points.add(new SyncRunLogTrendVO.Point(
                            r.getRunLogRunTime() == null ? "" : r.getRunLogRunTime().format(RUN_TIME_FORMAT),
                            r.getRunLogCostMs(),
                            r.getRunLogStatus()));
                }
            }
            return ReturnResult.ok(new SyncRunLogTrendVO(taskId, taskName, points));
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-log] 查询执行耗时趋势失败, taskId={}", taskId, e);
            return ReturnResult.error("查询执行耗时趋势失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SyncTaskRunVO> rerunFromLog(Long logId) {
        try {
            if (logId == null) {
                throw new IllegalArgumentException("日志 ID 不能为空");
            }
            MsSyncTaskRunLog row = runLogMapper.selectById(logId);
            if (row == null) {
                throw new IllegalStateException("日志不存在（可能已被 30 天保留策略清理）");
            }
            if (row.getRunLogTaskId() == null) {
                throw new IllegalStateException("该日志没有关联任务，无法重跑");
            }
            return runOnce(row.getRunLogTaskId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-log] 从日志重跑任务失败, logId={}", logId, e);
            return ReturnResult.error("重跑失败: " + e.getMessage());
        }
    }

    /**
     * 从失败日志正文归纳「失败原因」标签（基于 ERROR 行稳定关键段）。
     *
     * <p>规则先拍板成稳定文案：按顺序匹配已知关键词（源表问题 / 目标表问题 / 连接问题 /
     * 超时 / 权限 / 主键冲突 / 字段问题 / HTTP / 读取失败），命中即返回该标签；
     * 未命中返回「其他错误」。匹配顺序即优先级，避免「连接超时」同时命中连接与超时，
     * 也避免「源表不存在」被宽泛的「表不存在」误归为「目标表不存在」。
     * 注意：源读取类规则必须排在「表不存在」之前（源失败文案常同时含「源表不存在/读取失败」）。</p>
     *
     * @param lines 失败日志正文（{@code 时间\t级别\t内容} 逐行拼接）
     * @return 失败原因标签；正文为空返回 null
     */
    private String classifyFailure(String lines) {
        if (lines == null || lines.isBlank()) {
            return null;
        }
        // 源读取类优先：文案「源表不存在或无法读取」「读取失败」「Can't read」等指向源侧
        if (lines.contains("源表不存在") || lines.contains("源读取失败")
                || lines.contains("无法读取") || lines.contains("Can't read")
                || lines.contains("CannotReadFileException")) {
            return "源读取失败";
        }
        // 目标表问题：精确关键词优先，避免与源侧混淆
        if (lines.contains("目标表不存在") || lines.contains("目标表创建失败")
                || lines.contains("doesn't exist") || lines.contains("不存在的表")) {
            return "目标表不存在";
        }
        if (lines.contains("没有写权限") || lines.contains("Access denied") || lines.contains("denied")) {
            return "权限不足";
        }
        if (lines.contains("Duplicate entry") || lines.contains("主键冲突") || lines.contains("唯一键")) {
            return "主键/唯一键冲突";
        }
        if (lines.contains("Connection timed out") || lines.contains("connect timed out")) {
            return "连接超时";
        }
        if (lines.contains("UnknownHostException") || lines.contains("无法连接")) {
            return "无法连接主机";
        }
        if (lines.contains("connection refused") || lines.contains("Connection refused")) {
            return "连接被拒绝";
        }
        if (lines.contains("字段映射") || lines.contains("列不存在") || lines.contains("Unknown column")) {
            return "字段/列问题";
        }
        if (lines.contains("HTTP") && (lines.contains("超时") || lines.contains("timeout") || lines.contains("status"))) {
            return "HTTP 请求失败";
        }
        if (lines.contains("读取失败") || lines.contains("SQLException")) {
            return "源读取失败";
        }
        if (lines.contains("写入失败")) {
            return "写入失败";
        }
        return "其他错误";
    }

    /**
     * 字符串是否有内容（查询过滤用，null/空白视为未传）。
     */
    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 小时级时间键格式（健康度 24h 分布用，如 {@code yyyy-MM-dd HH:00}）。
     */
    private static final DateTimeFormatter HOUR_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");

    /**
     * 执行一次同步：读源、按映射转换、写目标，并回写任务状态。
     *
     * <p>执行本身不抛异常给调用方，而是把失败编码进返回值：同步任务失败是**业务结果**而不是
     * 接口故障，界面需要看到「读了几行、卡在哪一步」，用异常会只剩一句错误。</p>
     *
     * <p>全过程同步写入调试日志缓冲（begin → 逐步 info/error → end），
     * 进行中的日志可被 {@link #runLogs(Long)} 轮询到，用于前端实时排障。</p>
     *
     * @param task    任务
     * @param trigger 触发方式（见 {@link SyncTaskRunLogVO} 的 TRIGGER_* 常量）
     * @return 执行结果
     */
    private SyncTaskRunVO execute(MsSyncTask task, String trigger) {
        Long taskId = task.getId();
        AtomicBoolean running = runningTasks.computeIfAbsent(taskId, key -> new AtomicBoolean(false));
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("任务正在执行中，请等本次结束");
        }
        LocalDateTime runTime = LocalDateTime.now();
        long startNanos = System.nanoTime();
        long readCount = 0L;
        // 初始化为 null：finally 里要发完成通知，Error 等未被 catch 的路径下 result 仍为 null（通知跳过）
        SyncTaskRunVO result = null;
        String logStatus = SyncTaskRunVO.STATUS_FAILED;
        runLogBuffer.begin(taskId, trigger);
        try {
            // 缺配置在这里拦成一句人话（存量行可能没有路径/数据源），走统一失败路径而不是 NPE
            validateTaskForRun(task);
            SyncSourceType sourceType = SyncSourceType.from(task.getSyncTaskSourceType());
            runLogBuffer.info(taskId, "任务「" + task.getSyncTaskName() + "」开始执行（触发 " + trigger
                    + "，源类型 " + sourceType + "）");
            if (SyncMode.from(task.getSyncTaskSyncMode()) == SyncMode.INCREMENTAL) {
                runLogBuffer.info(taskId, "增量配置：字段 " + task.getSyncTaskIncField()
                        + "，当前水位 " + (task.getSyncTaskLastSeq() == null || task.getSyncTaskLastSeq().isBlank()
                        ? "（无，首跑按升序分批引导）" : task.getSyncTaskLastSeq())
                        + "，每批上限 " + (task.getSyncTaskIncBatchSize() == null
                        ? "不限" : task.getSyncTaskIncBatchSize() + " 条"));
            } else {
                runLogBuffer.info(taskId, "全量模式：本轮读取全部源数据");
            }
            if (task.getSyncTaskHookUrl() != null && !task.getSyncTaskHookUrl().isBlank()) {
                runLogBuffer.info(taskId, "Hook：" + task.getSyncTaskHookUrl()
                        + "（" + hookNotifier.effectiveNotify(task).label() + "，失败只记日志不影响同步）");
            }
            if (sourceType == SyncSourceType.DIR) {
                // 目录模式：逐个文件独立映射与写入，单文件失败不影响其余文件
                runLogBuffer.info(taskId, "目录源 " + task.getSyncTaskSourcePath());
                result = executeDirectory(task, runTime, startNanos);
            } else if (sourceType.isHttp()) {
                // 网络源：单次请求同时取列与数据（见 executeHttp）
                result = executeHttp(task, runTime, startNanos);
            } else {
                if (sourceType == SyncSourceType.JDBC) {
                    runLogBuffer.info(taskId, "源数据源 ID " + task.getSyncTaskSourceDatasourceId()
                            + "，源 SQL：" + task.getSyncTaskSourceSql());
                } else {
                    runLogBuffer.info(taskId, "源文件 " + task.getSyncTaskSourcePath());
                }
                List<SyncTargetPlan> targets = resolveTargets(task);
                if (targets.isEmpty()) {
                    throw new IllegalStateException("没有配置任何目标表，请编辑任务补全");
                }
                runLogBuffer.info(taskId, "输出目标 " + targets.size() + " 个："
                        + targets.stream().map(SyncTargetPlan::label).toList());
                List<SyncColumnVO> sourceColumns = resolveSourceColumns(task);
                runLogBuffer.info(taskId, "源列（" + sourceColumns.size() + " 列）：" + describeColumns(sourceColumns));
                long readTotal = 0L;
                long writeTotal = 0L;
                List<String> failedTargets = new ArrayList<>();
                List<String> targetMessages = new ArrayList<>();
                // 多目标逐个独立执行：每个目标自己重读一次源（流式，不落内存），
                // 独立事务、独立水位、独立失败。一个目标失败不阻断其余目标，
                // 全部结果汇总进 message，界面能看清「哪张表失败、为什么」。
                for (int i = 0; i < targets.size(); i++) {
                    SyncTargetPlan plan = targets.get(i);
                    String prefix = "[" + (i + 1) + "/" + targets.size() + "] " + plan.label();
                    runLogBuffer.info(taskId, prefix + " 开始：" + plan.writeMode()
                            + " / " + plan.syncMode());
                    try {
                        AtomicLong readCounter = new AtomicLong();
                        Path file = sourceType == SyncSourceType.FILE ? Path.of(task.getSyncTaskSourcePath()) : null;
                        long writeCount = streamAndWriteTo(task, plan, sourceColumns, readCounter, file);
                        readTotal += readCounter.get();
                        writeTotal += writeCount;
                        targetMessages.add(prefix + " 写入 " + writeCount + " 行");
                        runLogBuffer.info(taskId, prefix + " 完成：写 " + writeCount + " 行");
                    } catch (Exception e) {
                        String reason = rootMessage(e);
                        failedTargets.add(plan.label());
                        targetMessages.add(prefix + " 失败：" + reason);
                        runLogBuffer.error(taskId, prefix + " 失败：" + reason, e);
                    }
                }
                readCount = readTotal;
                if (failedTargets.isEmpty()) {
                    result = SyncTaskRunVO.success(readTotal, writeTotal, costMs(startNanos), runTime);
                } else if (failedTargets.size() == targets.size()) {
                    result = SyncTaskRunVO.failed(readTotal, costMs(startNanos),
                            String.join("；", targetMessages), runTime);
                } else {
                    // 部分成功：状态按失败算（有目标没写进去），但把成功行数与明细都带上
                    result = new SyncTaskRunVO(SyncTaskRunVO.STATUS_FAILED, readTotal, writeTotal,
                            costMs(startNanos),
                            "部分目标失败（" + failedTargets.size() + "/" + targets.size() + "）："
                                    + String.join("；", targetMessages), runTime);
                }
            }
            logStatus = result.status();
        } catch (Exception e) {
            String message = rootMessage(e);
            log.error("[sync-task] 任务执行失败, id={}, message={}", taskId, message, e);
            runLogBuffer.error(taskId, "执行失败：" + message, e);
            result = SyncTaskRunVO.failed(readCount, costMs(startNanos), message, runTime);
        } finally {
            running.set(false);
            // 完成通知（成功/失败都发）必须在 end() 之前：end 之后写日志会被静默丢弃
            if (result != null) {
                sendDoneHook(task, trigger, result);
            }
            runLogBuffer.end(taskId, logStatus, costMs(startNanos));
        }
        persistRunResult(taskId, result);
        return result;
    }

    /**
     * 目录模式执行：逐个匹配文件独立「读 → 映射 → 写」，并应用处理完动作。
     *
     * <p>单个文件失败不中断整轮：目录里常同时存在正在写入的半成品文件，
     * 一次失败就让整轮失败，会让「实时录入」永远卡在第一个坏文件上。结果给出成功/失败计数
     * 与失败文件名，便于定位。</p>
     *
     * @param task       任务
     * @param runTime    本次执行时间
     * @param startNanos 起始纳秒
     * @return 执行结果
     */
    private SyncTaskRunVO executeDirectory(MsSyncTask task, LocalDateTime runTime, long startNanos) {
        SyncSourceOptions options = optionsOf(task);
        long readTotal = 0L;
        long writeTotal = 0L;
        int okCount = 0;
        int failedCount = 0;
        StringBuilder problems = new StringBuilder();
        List<Path> files;
        try {
            files = listFiles(Path.of(task.getSyncTaskSourcePath()), options);
        } catch (Exception e) {
            runLogBuffer.error(task.getId(), "读取目录失败：" + rootMessage(e), e);
            return SyncTaskRunVO.failed(0L, costMs(startNanos), rootMessage(e), runTime);
        }
        runLogBuffer.info(task.getId(), "目录选项：pattern=" + options.pattern()
                + "，format=" + options.format() + "，处理完=" + options.afterProcess()
                + "，共匹配 " + files.size() + " 个文件");
        if (files.isEmpty()) {
            // 空目录不是错误：实时录入场景下一次触发可能刚好没有待处理文件
            runLogBuffer.info(task.getId(), "目录下没有待处理文件，本轮结束");
            return SyncTaskRunVO.success(0L, 0L, costMs(startNanos), runTime);
        }
        List<SyncTargetPlan> targets = resolveTargets(task);
        if (targets.isEmpty()) {
            throw new IllegalStateException("没有配置任何目标表，请编辑任务补全");
        }
        runLogBuffer.info(task.getId(), "输出目标 " + targets.size() + " 个："
                + targets.stream().map(SyncTargetPlan::label).toList());
        for (Path file : files) {
            List<SyncColumnVO> sourceColumns;
            try {
                sourceColumns = headersOf(file, options);
            } catch (Exception e) {
                failedCount++;
                appendProblem(problems, file.getFileName(), rootMessage(e));
                runLogBuffer.error(task.getId(), "文件 " + file.getFileName() + " 读表头失败：" + rootMessage(e), e);
                continue;
            }
            boolean fileOk = true;
            for (SyncTargetPlan plan : targets) {
                String prefix = "[" + plan.label() + "] ";
                try {
                    AtomicLong readCounter = new AtomicLong();
                    long writeCount = streamAndWriteTo(task, plan, sourceColumns, readCounter, file);
                    readTotal += readCounter.get();
                    writeTotal += writeCount;
                    runLogBuffer.info(task.getId(), prefix + "文件 " + file.getFileName() + "：读 "
                            + readCounter.get() + " 行 / 写 " + writeCount + " 行");
                } catch (Exception e) {
                    fileOk = false;
                    appendProblem(problems, file.getFileName(), plan.label() + " " + rootMessage(e));
                    runLogBuffer.error(task.getId(), prefix + "文件 " + file.getFileName()
                            + " 同步失败：" + rootMessage(e), e);
                    log.warn("[sync-task] 文件同步失败, task={}, file={}, target={}, message={}",
                            task.getId(), file, plan.label(), rootMessage(e));
                }
            }
            if (fileOk) {
                okCount++;
                // 只有**全部目标**都写成功才应用「处理完」动作：删掉/归档一个只写进一半目标的文件，
                // 会让失败的那张表永远补不回来
                afterProcess(task, file, options);
            } else {
                failedCount++;
            }
        }
        runLogBuffer.info(task.getId(), "目录执行完成：成功 " + okCount + " / 失败 " + failedCount
                + " 个文件，累计读 " + readTotal + " 行 / 写 " + writeTotal + " 行");
        if (failedCount == 0) {
            return SyncTaskRunVO.success(readTotal, writeTotal, costMs(startNanos), runTime);
        }
        String message = "处理 " + files.size() + " 个文件，成功 " + okCount + " / 失败 " + failedCount + "：" + problems;
        if (readTotal > 0 || writeTotal > 0) {
            // 部分文件失败：状态按失败算，但把已写入的量带上，界面能判断要不要重跑
            return new SyncTaskRunVO(SyncTaskRunVO.STATUS_FAILED, readTotal, writeTotal,
                    costMs(startNanos), message, runTime);
        }
        return SyncTaskRunVO.failed(readTotal, costMs(startNanos), message, runTime);
    }

    /**
     * 追加一条失败摘要（限制长度，避免几百个文件的错误把消息撑爆）。
     *
     * @param problems 摘要缓冲
     * @param file     文件名
     * @param reason   原因
     */
    private static void appendProblem(StringBuilder problems, Object file, String reason) {
        if (problems.length() < 300) {
            problems.append(file).append(": ").append(reason).append("; ");
        }
    }

    /**
     * 预览用：按输入源类型取源列。
     *
     * <p>与执行路径同源（{@link #resolveSourceColumns} 也是这套分支），避免「预览能配、执行时报错」。
     * 差别只有一点：预览拿不到任务实体，用的是请求里的临时参数。</p>
     *
     * <p><b>JDBC + {@code verify=false}（预览）</b>：字段直接由 SQL 文本解析
     * （{@link SyncJdbcSupport#parseColumns}），不连源库。若语句是 {@code SELECT *}
     * 或含无别名的表达式，文本里看不出字段名，此时才回落到一次带方言分页的探针查询。</p>
     *
     * <p><b>JDBC + {@code verify=true}（校验）</b>：真跑一次，但按方言只取 1 条
     * （见 {@link SyncJdbcSupport#probeColumns}），既能确认表/列存在，又不会因数据量大而拖垮。</p>
     *
     * @param request 预览入参
     * @param verify  是否真查一次源（校验为 true，预览为 false）
     * @return 源列
     * @throws Exception 源不可用
     */
    private List<SyncColumnVO> resolvePreviewSourceColumns(SyncPreviewDTO request, boolean verify) throws Exception {
        SyncSourceType type = SyncSourceType.from(request.sourceType());
        if (type == SyncSourceType.JDBC) {
            validateSelectSql(request.sourceSql());
            if (!verify) {
                List<SyncColumnVO> parsed = SyncJdbcSupport.parseColumns(request.sourceSql());
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            }
            return loadSourceColumns(request.sourceDatasourceId(), request.sourceSql());
        }
        String path = request.sourcePath() == null ? "" : request.sourcePath().trim();
        if (path.isBlank()) {
            throw new IllegalArgumentException("源地址不能为空（文件/目录填本地路径，HTTP 源填请求地址）");
        }
        if (type.isHttp()) {
            return httpColumnsOf(type, path, parseHttpOptions(request.sourceOptions()));
        }
        SyncSourceOptions options = parseSourceOptions(request.sourceOptions());
        Path sample = type == SyncSourceType.FILE
                ? Path.of(path)
                : listFiles(Path.of(path), options).stream().findFirst().orElse(null);
        if (sample == null) {
            throw new IllegalStateException("目录下没有匹配 " + options.pattern() + " 的文件: " + path);
        }
        return headersOf(sample, options);
    }

    /**
     * 按目标解析字段映射。
     *
     * <p>每个目标有各自的表结构与映射，因此不能共用一个配对结果。</p>
     *
     * @param task          任务
     * @param plan          目标计划
     * @param sourceColumns 源结果集列
     * @return 字段映射
     * @throws Exception 读取目标表结构失败
     */
    private List<SyncFieldMappingVO> resolveMappingFor(MsSyncTask task, SyncTargetPlan plan,
                                                       List<SyncColumnVO> sourceColumns) throws Exception {
        String mappingJson = plan.columnMapping();
        Long datasourceId = plan.targetDatasourceId();
        String targetTable = plan.targetTable();
        List<SyncFieldMappingVO> mapping = parseMapping(mappingJson);
        boolean explicit = !mapping.isEmpty();
        if (mapping.isEmpty()) {
            mapping = autoMatch(sourceColumns, loadTargetColumns(datasourceId, targetTable));
        }
        runLogBuffer.info(task.getId(), (explicit ? "显式字段映射" : "自动配对字段映射") + "（"
                + mapping.size() + " 项）：" + describeMapping(mapping));
        if (mapping.isEmpty()) {
            runLogBuffer.warn(task.getId(), "字段映射为空：源列与目标列没有可配对项");
            throw new IllegalStateException("字段映射为空：源列与目标列没有可配对项，请先在「字段映射」里配置");
        }
        Set<String> sourceNames = SyncJdbcSupport.columnNames(sourceColumns);
        for (SyncFieldMappingVO item : mapping) {
            if (!sourceNames.contains(item.source())) {
                runLogBuffer.warn(task.getId(), "字段映射引用了源结果集里不存在的列: " + item.source());
                throw new IllegalStateException("字段映射引用了源结果集里不存在的列: " + item.source());
            }
        }
        return mapping;
    }

    /**
     * 流式执行：引擎源读取 → 引擎字段映射转换 → 本模块 Sink 分批写入。
     *
     * <p>刻意不把整表读进 List：2026-10-02 实测 20 万行就会把堆顶到上限
     * （{@code -Xmx1500m} 下 RSS 1452MB），百万行必然 OOM。改流式后常驻内存只与批量大小有关。</p>
     *
     * @param task          任务
     * @param mapping       字段映射
     * @param sourceColumns 源结果集列（引擎按列名取值，必须与读取 SQL 的结果集一致）
     * @param readCounter   出参：累计读取行数（流式管线里没法用返回值一起带回来）
     * @return 实际写入行数
     * @throws Exception 建连、读取或写入失败
     */
    private long streamAndWriteTo(MsSyncTask task, SyncTargetPlan plan, List<SyncColumnVO> sourceColumns,
                                  AtomicLong readCounter, Path file) throws Exception {
        String[] columnNames = sourceColumns.stream().map(SyncColumnVO::name).toArray(String[]::new);
        JdbcDataSyncAgentSource jdbcSource = null;
        SyncSourceType type = SyncSourceType.from(task.getSyncTaskSourceType());
        boolean incremental = plan.incremental();
        AtomicReference<String> seqMax = new AtomicReference<>();
        Flux<Map<String, Object>> rows;
        if (type == SyncSourceType.JDBC) {
            // JDBC：引擎的 JDBC 源
            DatasourceJdbcInfo sourceInfo = datasourceService.jdbcInfo(task.getSyncTaskSourceDatasourceId());
            runLogBuffer.info(task.getId(), "源连接：" + sourceInfo.url()
                    + "（用户 " + sourceInfo.username() + "），开始流式读取");
            String readSql = task.getSyncTaskSourceSql();
            List<String> incFields = incremental ? plan.incFields() : List.of();
            if (!incFields.isEmpty()) {
                // 包裹层只加条件与排序，列集与内层一致；复用内层列核对，不为校验多打一次元数据
                requireFieldsInColumns(incFields, sourceColumns);
                readSql = buildIncrementalSql(task, plan, incFields);
                runLogBuffer.info(task.getId(), "增量读取 SQL：" + readSql);
            }
            jdbcSource = new JdbcDataSyncAgentSource(
                    "sync-src-" + task.getId(), "sync-in-" + task.getId(),
                    sourceInfo.url(), sourceInfo.username(), sourceInfo.password(),
                    readSql, columnNames);
            rows = jdbcSource.read(new HashMap<>());
            if (!incFields.isEmpty()) {
                // 写入前逐行滚动记录本批最大增量值（映射前的源行键名才是源列名）
                rows = rows.doOnNext(row -> trackSeqs(seqMax, row, incFields));
            }
        } else if (type.isHttp()) {
            // 网络源由 executeHttp 走「单次请求」路径，这里不该被调到；显式抛错而不是静默走错
            throw new IllegalStateException("HTTP 源不走流式路径");
        } else {
            if (incremental) {
                // validateTaskForRun 已拦；再兜一次，绝不让文件源悄悄按增量跑
                throw new IllegalStateException("文件/目录源不支持增量模式");
            }
            // 文件：csv 走引擎定界符源，excel 走 POI（见 MonitorFileRowsSupport）
            runLogBuffer.info(task.getId(), "解析源文件 " + file);
            rows = MonitorFileRowsSupport.read(file, optionsOf(task));
        }
        try {
            // 每个目标的映射各自解析：多目标时目标表不同，自动配对结果也不同
            List<SyncFieldMappingVO> mapping = resolveMappingFor(task, plan, sourceColumns);
            long written = writeRowsTo(task, plan, mapping, rows, readCounter, sourceColumns);
            if (incremental) {
                advanceTargetWatermark(task, plan, readCounter.get(), seqMax.get());
            }
            return written;
        } finally {
            if (jdbcSource != null) {
                jdbcSource.close();
            }
        }
    }

    /**
     * 网络源执行：<b>只发一次</b>请求，响应同时用于推导源列与写库。
     *
     * <p>为什么不「先探列再取数」：HTTP 接口常见一次性 token、签名时间戳、限流，
     * 打两次请求时第二次就会失败或拿到与第一次不同的快照，表现为字段映射对不上、或干脆 401。
     * 先取一次数据、由数据推列名，既省一次往返，也保证列与数据同源。</p>
     *
     * @param task       任务
     * @param runTime    本次执行时间
     * @param startNanos 起始纳秒
     * @return 执行结果
     * @throws Exception 请求或写入失败
     */
    private SyncTaskRunVO executeHttp(MsSyncTask task, LocalDateTime runTime, long startNanos) throws Exception {
        List<SyncTargetPlan> targets = resolveTargets(task);
        if (targets.isEmpty()) {
            throw new IllegalStateException("没有配置任何目标表，请编辑任务补全");
        }
        SyncSourceType type = SyncSourceType.from(task.getSyncTaskSourceType());
        // 一次请求只取一份数据，供全部目标共用：网络源常见一次性 token / 签名时间戳 / 限流，
        // 给每个目标各打一次请求既慢又容易在第二次就失败。
        // 水位取「唯一一个增量目标」的：多目标各自水位不同时无法用一次请求同时满足，
        // 这种组合在保存时就已按目标独立校验，执行时按第一个增量目标取数，
        // 其余目标的写入不受影响（保存期已禁止把「先清空再插入」配给增量目标）。
        SyncTargetPlan pullPlan = targets.stream().filter(SyncTargetPlan::incremental).findFirst().orElse(null);
        final List<String> incFields = pullPlan == null ? List.of() : pullPlan.incFields();
        String url = task.getSyncTaskSourcePath();
        if (!incFields.isEmpty()) {
            String lastSeq = pullPlan.lastSeq();
            List<String> values = parseSeqValues(lastSeq, incFields.size());
            if (values.isEmpty()) {
                runLogBuffer.info(task.getId(), "增量请求：首跑无水位，不携带 "
                        + String.join(",", incFields) + " 参数");
            } else {
                // 复合水位按字段逐个还原成查询参数，服务端按 (a,b) 字典序续读
                for (int i = 0; i < incFields.size(); i++) {
                    url = appendQuery(url, incFields.get(i), values.get(i));
                }
                runLogBuffer.info(task.getId(), "增量请求：携带 "
                        + String.join(",", incFields) + "=" + String.join(",", values));
            }
        }
        runLogBuffer.info(task.getId(), "发起 HTTP 请求：" + url);
        List<Map<String, Object>> rows = MonitorHttpRowsSupport.rows(type, url, httpOptionsOf(task));
        if (rows.isEmpty()) {
            runLogBuffer.warn(task.getId(), "HTTP 响应为空，本轮读 0 行");
            return SyncTaskRunVO.success(0L, 0L, costMs(startNanos), runTime);
        }
        List<SyncColumnVO> sourceColumns = columnsOfRows(rows);
        runLogBuffer.info(task.getId(), "响应解析：" + rows.size() + " 行 / " + sourceColumns.size()
                + " 列（" + describeColumns(sourceColumns) + "）");
        if (!incFields.isEmpty()) {
            requireFieldsInColumns(incFields, sourceColumns);
            Integer batch = pullPlan.incBatchSize();
            if (batch != null && batch > 0 && rows.size() > batch) {
                // 服务端若一次给多于每批上限：升序取最老的 N 条（倒序先取最新会把水位推过未同步数据）
                int total = rows.size();
                List<Map<String, Object>> sorted = new ArrayList<>(rows);
                sorted.sort(Comparator.comparing(
                        (Map<String, Object> row) -> seqOfIncFields(row, incFields),
                        Comparator.nullsLast(SyncTaskServiceImpl::seqCompare)));
                rows = new ArrayList<>(sorted.subList(0, batch));
                runLogBuffer.info(task.getId(), "按每批上限截取最老的 " + batch
                        + " 条（响应共 " + total + " 行，其余数据下轮继续）");
            }
        }
        long readTotal = 0L;
        long writeTotal = 0L;
        List<String> failedTargets = new ArrayList<>();
        List<String> targetMessages = new ArrayList<>();
        // 已取到的行复用给每个目标，不再回源
        for (int i = 0; i < targets.size(); i++) {
            SyncTargetPlan plan = targets.get(i);
            String prefix = "[" + (i + 1) + "/" + targets.size() + "] " + plan.label();
            try {
                AtomicLong readCounter = new AtomicLong();
                long writeCount = writeRowsTo(task, plan, resolveMappingFor(task, plan, sourceColumns),
                        Flux.fromIterable(rows), readCounter, sourceColumns);
                readTotal += readCounter.get();
                writeTotal += writeCount;
                targetMessages.add(prefix + " 写入 " + writeCount + " 行");
                runLogBuffer.info(task.getId(), prefix + " 完成：写 " + writeCount + " 行");
                if (plan.incremental()) {
                    AtomicReference<String> seqMax = new AtomicReference<>();
                    for (Map<String, Object> row : rows) {
                        trackSeqs(seqMax, row, incFields);
                    }
                    advanceTargetWatermark(task, plan, readCounter.get(), seqMax.get());
                }
            } catch (Exception e) {
                String reason = rootMessage(e);
                failedTargets.add(plan.label());
                targetMessages.add(prefix + " 失败：" + reason);
                runLogBuffer.error(task.getId(), prefix + " 失败：" + reason, e);
            }
        }
        runLogBuffer.info(task.getId(), "执行完成：读 " + readTotal + " 行 / 写 " + writeTotal + " 行");
        if (failedTargets.isEmpty()) {
            return SyncTaskRunVO.success(readTotal, writeTotal, costMs(startNanos), runTime);
        }
        String message = "部分目标失败（" + failedTargets.size() + "/" + targets.size() + "）："
                + String.join("；", targetMessages);
        return new SyncTaskRunVO(SyncTaskRunVO.STATUS_FAILED, readTotal, writeTotal,
                costMs(startNanos), message, runTime);
    }

    /**
     * 由行数据推出源列（各行键的并集，按首次出现顺序）。
     *
     * <p>JSON 里各对象的字段常常不齐（有的行没有 {@code remark}），取并集才能让可选列
     * 也出现在字段映射的可选项里。</p>
     *
     * @param rows 行数据
     * @return 源列
     */
    private List<SyncColumnVO> columnsOfRows(List<Map<String, Object>> rows) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            for (String key : row.keySet()) {
                if (!names.contains(key)) {
                    names.add(key);
                }
            }
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("HTTP 响应里没有解析到任何列");
        }
        return names.stream().map(name -> new SyncColumnVO(name, "string", "")).toList();
    }

    /**
     * 按某个目标写入：字段映射转换 + 该目标自己的 Sink。
     *
     * <p>多目标时每个目标各调一次，映射与写入模式都取自该目标，互不干扰。</p>
     *
     * @param task          任务
     * @param plan          目标计划（必有）
     * @param mapping       字段映射
     * @param rows          源行流
     * @param readCounter   出参：累计读取行数
     * @param sourceColumns 源列（保留给调用方语义，当前不再参与配对）
     * @return 实际写入行数
     * @throws Exception 映射转换或写入失败
     */
    private long writeRowsTo(MsSyncTask task, SyncTargetPlan plan, List<SyncFieldMappingVO> mapping,
                             Flux<Map<String, Object>> rows, AtomicLong readCounter,
                             List<SyncColumnVO> sourceColumns) throws Exception {
        List<DataSyncFieldMapping> engineMappings = new ArrayList<>(mapping.size());
        for (SyncFieldMappingVO item : mapping) {
            engineMappings.add(new SimpleFieldMapping(item.source(), item.target(), null));
        }
        // 每条数据通知（仅增量 + ROW 时机）：挂在映射之后——推送的是写进目标表的数据；
        // seq 从映射前的源行取（增量字段是源列，映射后键名可能已变成目标列名）
        boolean rowHook = hookNotifier.effectiveNotify(task) == SyncHookNotify.ROW
                && task.getSyncTaskHookUrl() != null && !task.getSyncTaskHookUrl().isBlank();
        final List<String> incFields = rowHook ? plan.incFields() : List.of();
        // 引擎的源以 Flux 暴露数据；params 不含 offset 即全量读
        Flux<Map<String, Object>> mapped = rows
                .doOnNext(row -> readCounter.incrementAndGet())
                .map(row -> {
                    Map<String, Object> converted = fieldMappingConverter.applyMappings(row, engineMappings);
                    if (rowHook) {
                        hookNotifier.notifyRow(task, converted, seqOfIncFields(row, incFields));
                    }
                    return converted;
                });
        long written = buildSinkFor(task, plan, mapping).writeStream(mapped);
        runLogBuffer.info(task.getId(), "写入完成：读 " + readCounter.get() + " 行 / 写 " + written + " 行");
        return written;
    }

    /**
     * 把「逗号分隔的有序增量字段串」拆成列表。
     *
     * @param incField 增量字段串，可为空
     * @return 去空白后的字段列表（保持顺序、去重）
     */
    private static List<String> parseIncFields(String incField) {
        if (incField == null || incField.isBlank()) {
            return List.of();
        }
        List<String> fields = new ArrayList<>(3);
        for (String part : incField.split(",")) {
            String field = part.trim();
            if (!field.isEmpty() && !fields.contains(field)) {
                fields.add(field);
            }
        }
        return fields;
    }

    /**
     * 从源行取出复合增量水位值。
     *
     * @param row       源行
     * @param incFields 增量字段（按顺序）
     * @return 水位串；取不到返回 null
     */
    private static String seqOfIncFields(Map<String, Object> row, List<String> incFields) {
        if (incFields == null || incFields.isEmpty()) {
            return null;
        }
        if (incFields.size() == 1) {
            return seqOf(row.get(incFields.get(0)));
        }
        List<String> values = new ArrayList<>(incFields.size());
        for (String field : incFields) {
            String value = seqOf(row.get(field));
            if (value == null || value.isEmpty()) {
                return null;
            }
            values.add(value);
        }
        return joinSeqValues(values);
    }

    /**
     * 按目标配置构造目标 Sink。
     *
     * @param task    任务
     * @param plan    目标计划
     * @param mapping 字段映射（目标列顺序决定占位符顺序）
     * @return 目标 Sink
     * @throws Exception 目标数据源不可用或目标表名非法
     */
    private MonitorJdbcSyncSink buildSinkFor(MsSyncTask task, SyncTargetPlan plan,
                                             List<SyncFieldMappingVO> mapping) throws Exception {
        List<String> targetColumns = mapping.stream().map(SyncFieldMappingVO::target).toList();
        Long targetDatasourceId = plan.targetDatasourceId();
        String targetTable = plan.targetTable();
        SyncWriteMode mode = SyncWriteMode.from(plan.writeMode());
        int batch = batchSizeOf(plan.batchSize());
        DatasourceJdbcInfo targetInfo = datasourceService.jdbcInfo(targetDatasourceId);
        String quotedTable = SyncJdbcSupport.quoteTable(targetTable);
        String writeSql = buildWriteSql(mode, quotedTable, targetColumns);
        runLogBuffer.info(task.getId(), "写入计划：目标数据源 " + targetDatasourceId
                + "，表 " + targetTable + "，模式 " + mode + "，批量 " + batch);
        runLogBuffer.info(task.getId(), "写入语句：" + writeSql);
        return new MonitorJdbcSyncSink(
                // 多目标时每个目标一个 Sink 实例，ID 必须互不相同，否则日志/排障时分不清是哪张表
                "sync-sink-" + task.getId() + "-" + Integer.toHexString(quotedTable.hashCode()),
                targetInfo, writeSql, targetColumns,
                batch, quotedTable, mode == SyncWriteMode.OVERWRITE);
    }

    /**
     * 读取源 SQL 的结果集列。
     *
     * @param datasourceId 源数据源 ID
     * @param sql          源 SQL
     * @return 列列表
     * @throws Exception 数据源不可用或语句无法解析
     */
    private List<SyncColumnVO> loadSourceColumns(Long datasourceId, String sql) throws Exception {
        validateSelectSql(sql);
        try (Connection conn = datasourceService.connection(datasourceId)) {
            List<SyncColumnVO> columns = SyncJdbcSupport.columnsOf(conn, sql);
            if (columns.isEmpty()) {
                throw new IllegalStateException("源 SQL 没有返回任何列，请确认语句是否正确");
            }
            return columns;
        } catch (SQLException e) {
            // 源 SQL 里的表/列不存在时，驱动抛的是数据库原生串（MySQL
            // "Table 'db.tbl' doesn't exist"、PG  relation ... does not exist、
            // Oracle ORA-00942）。这类异常若落到 preview() 的通用 catch，会被包成
            // 「预览字段映射失败: ...」，前端既分不清是源侧还是目标侧缺表，
            // 也匹配不到目标侧的固定文案，于是设计表弹窗永远不弹。
            // 这里统一转成与目标侧同构的约定文案，异常类型也换成
            // IllegalStateException（preview() 的专用 catch 原样返回，不加前缀）。
            throw new IllegalStateException(sourceSqlFailureMessage(sql, e), e);
        }
    }

    /**
     * 源 SQL 执行失败的统一文案：固定前缀 + 原始方言信息。
     *
     * <p>前缀「源表不存在或无法读取」是前后端契约（前端 {@code isSourceTableMissingError}
     * 据此判定缺表在源侧）。保留原始信息便于排查（哪张表、什么方言错误），
     * 但不再让原生串裸奔成「预览字段映射失败」。</p>
     *
     * @param sql 源 SQL
     * @param e   驱动异常
     * @return 面向用户的错误文案
     */
    private String sourceSqlFailureMessage(String sql, SQLException e) {
        String detail = e.getMessage() == null ? "" : e.getMessage().trim();
        return "源表不存在或无法读取: " + detail + "（源 SQL: " + abbreviate(sql, 160) + "）";
    }

    /**
     * 截断长文本，避免把整段 SQL 写进 toast。
     *
     * @param text   原文
     * @param maxLen 最大长度
     * @return 截断结果
     */
    private String abbreviate(String text, int maxLen) {
        String value = text == null ? "" : text.trim();
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "…";
    }

    /**
     * 读取目标表的列定义。
     *
     * <p><strong>表不存在时的语义</strong>：{@link SyncJdbcSupport#tableColumns} 对不存在的表
     * 返回空列表（不再抛 MySQL 的 {@code Table ... doesn't exist} 原生异常），这里统一转换成
     * {@link IllegalStateException}。预览场景由 {@link #preview} 的 catch 兜住并返回
     * 「目标表不存在或没有可读列」，前端 {@code isTableMissingError} 匹配后自动弹设计表向导；
     * 执行场景由 {@link #resolveMapping} 继续走「字段映射为空」校验。两种场景都不会把
     * 数据库的原生 SQL 错误暴露给用户。</p>
     *
     * @param datasourceId 目标数据源 ID
     * @param table        目标表名
     * @return 列列表
     * @throws Exception 数据源不可用；表不存在时抛 {@link IllegalStateException}
     */
    private List<SyncColumnVO> loadTargetColumns(Long datasourceId, String table) throws Exception {
        SyncJdbcSupport.quoteTable(table);
        try (Connection conn = datasourceService.connection(datasourceId)) {
            List<SyncColumnVO> columns = SyncJdbcSupport.tableColumns(conn, table);
            if (columns.isEmpty()) {
                throw new IllegalStateException("目标表不存在或没有可读列: " + table);
            }
            return columns;
        }
    }

    /**
     * 由数据源连接信息构造写入语句。
     *
     * @param mode          写入模式
     * @param quotedTable   已引用的目标表名
     * @param targetColumns 目标列名（占位符顺序）
     * @return 写入语句
     */
    private String buildWriteSql(SyncWriteMode mode, String quotedTable, List<String> targetColumns) {
        StringBuilder columns = new StringBuilder();
        StringBuilder holders = new StringBuilder();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i > 0) {
                columns.append(", ");
                holders.append(", ");
            }
            String quoted = SyncJdbcSupport.quoteColumn(targetColumns.get(i));
            columns.append(quoted);
            holders.append('?');
        }
        if (mode == SyncWriteMode.REPLACE) {
            return "REPLACE INTO " + quotedTable + " (" + columns + ") VALUES (" + holders + ")";
        }
        String insert = "INSERT INTO " + quotedTable + " (" + columns + ") VALUES (" + holders + ")";
        if (mode != SyncWriteMode.UPSERT) {
            return insert;
        }
        StringBuilder updates = new StringBuilder();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i > 0) {
                updates.append(", ");
            }
            String quoted = SyncJdbcSupport.quoteColumn(targetColumns.get(i));
            updates.append(quoted).append("=VALUES(").append(quoted).append(')');
        }
        return insert + " ON DUPLICATE KEY UPDATE " + updates;
    }

    /**
     * 按「同名（忽略大小写）」在源列与目标列之间自动配对。
     *
     * @param sourceColumns 源列
     * @param targetColumns 目标列
     * @return 字段映射，可能为空
     */
    private List<SyncFieldMappingVO> autoMatch(List<SyncColumnVO> sourceColumns, List<SyncColumnVO> targetColumns) {
        Map<String, String> targetByLower = new LinkedHashMap<>();
        for (SyncColumnVO column : targetColumns) {
            targetByLower.putIfAbsent(column.name().toLowerCase(Locale.ROOT), column.name());
        }
        List<SyncFieldMappingVO> mapping = new ArrayList<>();
        for (SyncColumnVO column : sourceColumns) {
            String target = targetByLower.get(column.name().toLowerCase(Locale.ROOT));
            if (target != null) {
                mapping.add(new SyncFieldMappingVO(column.name(), target));
            }
        }
        return mapping;
    }

    /**
     * 汇总预览阶段的提示。
     *
     * @param sourceColumns 源列
     * @param targetColumns 目标列
     * @param mapping       最终映射
     * @param configured    映射是否由调用方显式配置
     * @return 提示列表
     */
    private List<String> collectWarnings(List<SyncColumnVO> sourceColumns, List<SyncColumnVO> targetColumns,
                                         List<SyncFieldMappingVO> mapping, boolean configured) {
        List<String> warnings = new ArrayList<>();
        Set<String> sourceNames = SyncJdbcSupport.columnNames(sourceColumns);
        Set<String> targetNames = SyncJdbcSupport.columnNames(targetColumns);
        Set<String> mappedSources = new java.util.LinkedHashSet<>();
        Set<String> mappedTargets = new java.util.LinkedHashSet<>();
        for (SyncFieldMappingVO item : mapping) {
            if (!sourceNames.contains(item.source())) {
                warnings.add("映射引用了源结果集里不存在的列: " + item.source());
            }
            if (!targetNames.contains(item.target())) {
                warnings.add("映射引用了目标表里不存在的列: " + item.target());
            }
            if (!mappedSources.add(item.source())) {
                warnings.add("源列被映射了多次: " + item.source());
            }
            if (!mappedTargets.add(item.target())) {
                warnings.add("目标列被映射了多次: " + item.target());
            }
        }
        for (String name : sourceNames) {
            if (!mappedSources.contains(name)) {
                warnings.add("源列未参与同步: " + name);
            }
        }
        for (String name : targetNames) {
            if (!mappedTargets.contains(name)) {
                warnings.add("目标列未写入: " + name);
            }
        }
        if (!configured) {
            warnings.add(0, "当前映射由「同名自动配对」得出，保存前请确认");
        }
        if (mapping.isEmpty()) {
            warnings.add("没有可用的字段映射，任务将无法执行");
        }
        return warnings;
    }

    /**
     * 校验任务入参，并把规范化后的值写进实体。
     *
     * @param task    目标实体
     * @param request 入参
     */
    private void applyRequest(MsSyncTask task, SyncTaskDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("任务参数不能为空");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        // 目标配置：多目标优先（targets），为空时回落旧的单目标三件套。
        // 两个入口都必须能用——老版本前端只发单目标字段，不能因为加了 targets 就把它判成「没配目标」。
        List<SyncTargetDTO> targets = normalizeTargets(request);
        boolean multiTarget = !targets.isEmpty();
        if (!multiTarget && request.targetDatasourceId() == null) {
            throw new IllegalArgumentException("目标数据源不能为空");
        }
        if (!multiTarget && (request.targetTable() == null || request.targetTable().isBlank())) {
            throw new IllegalArgumentException("目标表名不能为空");
        }
        // 输入源按类型校验：JDBC 用数据源 + SELECT；FILE/DIR 用服务器本地绝对路径；
        // HTTP_JSON/HTTP_HTML 用 http(s) 地址 + 各自选项，并在**保存时**就真打一次请求确认能取到列——
        // 网络源最容易配错（地址拼错、需要鉴权、选择器写错），存下来不校验等于把问题留到定时执行时才炸
        SyncSourceType sourceType = SyncSourceType.from(request.sourceType());
        String sourcePath = request.sourcePath() == null ? null : request.sourcePath().trim();
        SyncSourceOptions sourceOptions = SyncSourceOptions.defaults();
        SyncHttpOptions httpOptions = SyncHttpOptions.defaults().normalized();
        List<SyncColumnVO> reachableColumns = null;
        if (sourceType == SyncSourceType.JDBC) {
            if (request.sourceDatasourceId() == null) {
                throw new IllegalArgumentException("源数据源不能为空");
            }
            validateSelectSql(request.sourceSql());
            // 源/目标数据源在**保存时**就校验存在且为 jdbc：否则任务能存下来，
            // 直到执行才报「数据源不存在」，白白浪费一次执行与一轮排查
            validateDatasource(request.sourceDatasourceId(), "源数据源");
        } else if (sourceType.isHttp()) {
            if (sourcePath == null || sourcePath.isBlank()) {
                throw new IllegalArgumentException("请求地址不能为空");
            }
            httpOptions = parseHttpOptions(request.sourceOptions());
            reachableColumns = assertHttpSourceReachable(sourceType, sourcePath, httpOptions);
        } else {
            if (sourcePath == null || sourcePath.isBlank()) {
                throw new IllegalArgumentException("文件/目录路径不能为空");
            }
            Path path = Path.of(sourcePath);
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("文件/目录必须使用服务器本地绝对路径: " + sourcePath);
            }
            if (sourceType == SyncSourceType.FILE && !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("文件不存在或不是普通文件: " + sourcePath);
            }
            if (sourceType == SyncSourceType.DIR && !Files.isDirectory(path)) {
                throw new IllegalArgumentException("目录不存在: " + sourcePath);
            }
            sourceOptions = parseSourceOptions(request.sourceOptions());
        }
        if (!multiTarget) {
            // 提前跑一次白名单校验，别等执行时才拒
            SyncJdbcSupport.quoteTable(request.targetTable());
            validateDatasource(request.targetDatasourceId(), "目标数据源");
        }
        if (request.enabled() != null && request.enabled() == 1
                && (request.cron() == null || request.cron().isBlank())) {
            throw new IllegalArgumentException("启用定时前请填写定时表达式（cron）");
        }
        if (SyncWriteMode.from(request.writeMode()) == SyncWriteMode.UPSERT
                || SyncWriteMode.from(request.writeMode()) == SyncWriteMode.REPLACE) {
            // 仅提示语义，不做阻断：这两种模式在非 MySQL 目标上会由驱动报错，消息足够明确
            log.debug("[sync-task] 任务 {} 使用 {} 写入模式", request.name(), SyncWriteMode.from(request.writeMode()).code());
        }
        validateCron(request.cron());
        parseMapping(request.columnMapping());
        if (multiTarget) {
            applyTargets(task, request, targets, sourceType, reachableColumns);
        } else {
            applyModeAndHook(task, request, sourceType, reachableColumns);
        }

        task.setSyncTaskName(request.name().trim());
        task.setSyncTaskSourceType(sourceType.name());
        task.setSyncTaskSourceDatasourceId(sourceType == SyncSourceType.JDBC ? request.sourceDatasourceId() : null);
        task.setSyncTaskSourceSql(sourceType == SyncSourceType.JDBC ? request.sourceSql().trim() : null);
        task.setSyncTaskSourcePath(sourceType == SyncSourceType.JDBC ? null : sourcePath);
        task.setSyncTaskSourceOptions(sourceType == SyncSourceType.JDBC ? null
                : (sourceType.isHttp() ? toJson(httpOptions) : toJson(sourceOptions)));
        if (!multiTarget) {
            task.setSyncTaskTargetDatasourceId(request.targetDatasourceId());
            task.setSyncTaskTargetTable(request.targetTable().trim());
            task.setSyncTaskColumnMapping(normalizeMapping(request.columnMapping()));
            task.setSyncTaskWriteMode(SyncWriteMode.from(request.writeMode()).code());
            task.setSyncTaskBatchSize(batchSizeOf(request.batchSize()));
            // 从「多目标」退回「单目标」时，必须清掉上一版的多目标 JSON：
            // resolveTargets 优先读它，留着会让界面里已删掉的目标继续被写数据。
            if (task.getSyncTaskTargets() != null) {
                task.setSyncTaskTargets(null);
                task.setSyncTaskTargetSeqs(null);
            }
        }
        task.setSyncTaskCron(request.cron() == null || request.cron().isBlank() ? null : request.cron().trim());
        task.setSyncTaskEnabled(request.enabled() == null ? 0 : (request.enabled() == 1 ? 1 : 0));
    }

    /**
     * 归一化并校验入参里的多目标列表。
     *
     * <p>返回空列表表示「入参没走多目标」，调用方继续用旧的单目标三件套；
     * 返回非空列表时<b>每一项都已通过</b>表名白名单与数据源存在性校验——
     * 多目标最容易犯的错是「第二个目标的数据源 ID 抄错」，存下来要到执行才炸。</p>
     *
     * @param request 入参
     * @return 校验过的目标列表；未配置多目标返回空列表
     * @throws IllegalArgumentException 目标缺字段、重复或数据源不可用
     */
    private List<SyncTargetDTO> normalizeTargets(SyncTaskDTO request) {
        List<SyncTargetDTO> raw = request.targets();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<SyncTargetDTO> targets = new ArrayList<>(raw.size());
        Set<String> keys = new java.util.LinkedHashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            SyncTargetDTO item = raw.get(i);
            String prefix = "第 " + (i + 1) + " 个目标";
            if (item == null) {
                throw new IllegalArgumentException(prefix + "配置为空");
            }
            if (item.targetDatasourceId() == null) {
                throw new IllegalArgumentException(prefix + "未选择目标数据源");
            }
            if (item.targetTable() == null || item.targetTable().isBlank()) {
                throw new IllegalArgumentException(prefix + "未填写目标表名");
            }
            String table = item.targetTable().trim();
            SyncJdbcSupport.quoteTable(table);
            String key = item.targetDatasourceId() + "#" + table;
            if (!keys.add(key)) {
                throw new IllegalArgumentException(prefix + "与前面的目标重复（同一数据源的同一张表）：" + table);
            }
            // 增量字段就地归一化（去空白、丢空项、去重且保留选择顺序）：
            // 存进 JSON 的形态必须与界面上看到的一致，否则只是多了个空格就会把水位误判成「字段变了」而清掉
            List<String> incFields = item.incFields() == null ? List.of()
                    : item.incFields().stream()
                    .filter(field -> field != null && !field.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            targets.add(new SyncTargetDTO(item.targetDatasourceId(), table, item.writeMode(), item.syncMode(),
                    incFields.isEmpty() ? null : incFields, item.columnMapping(), item.batchSize(),
                    item.incBatchSize()));
        }
        return targets;
    }

    /**
     * 校验并写入多目标配置（每个目标一套独立的同步模式 / 写入模式 / 增量字段 / 映射）。
     *
     * <p><b>为什么每个目标都要单独校验增量字段</b>：多目标时一张表走全量、另一张走增量是
     * 合法组合，用任务级的单一 syncMode 去校验会把这种配置判死。所以这里逐目标校验，
     * 只在增量目标达到 1 个以上时才回源读一次列（复用同一份结果，不重复打库）。</p>
     *
     * <p><b>水位隔离</b>：每个目标的增量水位存在 {@code ms_sync_task_target_seqs} 里，
     * 键是「数据源ID#表名」。目标改了增量字段就把该键删掉——旧字段的水位对新字段没有意义，
     * 沿用会漏数据；其它目标的水位一个字都不动。</p>
     *
     * <p><b>兼容列</b>：第一个目标镜像回任务原有的单目标列，这样「旧版前端 / 直查库的报表」
     * 看到的仍是可用值，不因为升级成多目标就变空。</p>
     *
     * @param task             目标实体（更新时已带库里存量值）
     * @param request          入参
     * @param targets          已归一化的目标列表（非空）
     * @param sourceType       输入源类型
     * @param reachableColumns HTTP 源保存期探活取到的列（非 HTTP 源为 null）
     */
    private void applyTargets(MsSyncTask task, SyncTaskDTO request, List<SyncTargetDTO> targets,
                              SyncSourceType sourceType, List<SyncColumnVO> reachableColumns) {
        boolean anyIncremental = false;
        // HTTP 源一次请求只能带一套水位参数：多个增量目标若增量字段不同，
        // 就没法用同一次响应同时满足两边的续读条件（一边按 id、一边按 updated_at），
        // 这里在保存时直接拦掉，而不是执行时悄悄按第一个目标的字段取数导致另一个目标漏数据。
        List<String> httpIncFields = null;
        for (SyncTargetDTO target : targets) {
            validateDatasource(target.targetDatasourceId(), "目标数据源（" + target.targetTable() + "）");
            SyncWriteMode writeMode = SyncWriteMode.from(target.writeMode());
            SyncMode syncMode = SyncMode.from(target.syncMode());
            if (target.batchSize() != null && target.batchSize() < 1) {
                throw new IllegalArgumentException(target.targetTable() + "：批量提交条数必须为正整数");
            }
            if (syncMode != SyncMode.INCREMENTAL) {
                continue;
            }
            anyIncremental = true;
            if (sourceType != SyncSourceType.JDBC && !sourceType.isHttp()) {
                throw new IllegalArgumentException("增量模式仅支持 JDBC / HTTP 输入源，文件/目录源请用全量模式（"
                        + target.targetTable() + "）");
            }
            List<String> incFields = parseIncFields(target.incFields() == null ? null
                    : String.join(",", target.incFields()));
            if (incFields.isEmpty()) {
                throw new IllegalArgumentException(target.targetTable() + "：「" + request.name()
                        + "」的增量模式必须选择增量字段");
            }
            for (String incField : incFields) {
                if (!INC_FIELD_PATTERN.matcher(incField).matches()) {
                    throw new IllegalArgumentException(target.targetTable()
                            + "：增量字段名只允许字母、数字、下划线: " + incField);
                }
            }
            if (writeMode == SyncWriteMode.OVERWRITE) {
                throw new IllegalArgumentException(target.targetTable()
                        + "：增量模式不能使用「先清空再插入」——每批执行都会清空目标表");
            }
            if (target.incBatchSize() != null && target.incBatchSize() < 1) {
                throw new IllegalArgumentException(target.targetTable() + "：每批数量必须为正整数（留空表示不限）");
            }
            if (sourceType.isHttp()) {
                if (httpIncFields == null) {
                    httpIncFields = incFields;
                } else if (!httpIncFields.equals(incFields)) {
                    throw new IllegalArgumentException("HTTP 源只发一次请求，多个增量目标必须使用相同的增量字段（当前「"
                            + String.join(",", httpIncFields) + "」与「" + String.join(",", incFields)
                            + "」不一致）；请改成相同字段，或让其中一个目标走全量");
                }
            }
            List<SyncColumnVO> columns = sourceColumnsForValidate(request, sourceType, reachableColumns);
            requireFieldsInColumns(incFields, columns);
        }
        String previousJson = task.getSyncTaskTargets();
        task.setSyncTaskTargets(toJson(targets));
        // 增量字段变了的目标，其水位作废；没变的保留（全量目标没有水位，自然不受影响）
        voidChangedTargetSeqs(task, previousJson, targets);

        SyncTargetDTO first = targets.get(0);
        task.setSyncTaskTargetDatasourceId(first.targetDatasourceId());
        task.setSyncTaskTargetTable(first.targetTable());
        task.setSyncTaskColumnMapping(normalizeMapping(first.columnMapping()));
        task.setSyncTaskWriteMode(SyncWriteMode.from(first.writeMode()).code());
        task.setSyncTaskBatchSize(batchSizeOf(first.batchSize()));
        // 任务级同步模式 / 增量字段只是「兼容与列表展示」用：取第一个目标的值；
        // 只要有一个目标走增量，任务级就标成增量，避免列表把它显示成「全量」而误导。
        task.setSyncTaskSyncMode(anyIncremental ? SyncMode.INCREMENTAL.code() : SyncMode.FULL.code());
        task.setSyncTaskIncField(first.incFields() == null || first.incFields().isEmpty()
                ? null : String.join(",", first.incFields()));
        task.setSyncTaskIncBatchSize(first.incBatchSize());
        applyHookConfig(task, request);
    }

    /**
     * 校验期取源列：JDBC 真读一次结果集列（带方言分页 1 条），HTTP 复用保存期探活结果。
     *
     * @param request          入参
     * @param sourceType       源类型
     * @param reachableColumns HTTP 探活列（非 HTTP 源为 null）
     * @return 源列
     */
    private List<SyncColumnVO> sourceColumnsForValidate(SyncTaskDTO request, SyncSourceType sourceType,
                                                        List<SyncColumnVO> reachableColumns) {
        if (sourceType != SyncSourceType.JDBC) {
            return reachableColumns;
        }
        try {
            return loadSourceColumns(request.sourceDatasourceId(), request.sourceSql());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取源结果集列失败: " + rootMessage(e));
        }
    }

    /**
     * 增量字段变化的目标作废其水位（其它目标不受影响）。
     *
     * @param task         任务（含库里存量的目标配置与水位）
     * @param previousJson 库里存量的多目标 JSON（本次提交前的值，可为空）
     * @param targets      本次提交的目标列表
     */
    private void voidChangedTargetSeqs(MsSyncTask task, String previousJson, List<SyncTargetDTO> targets) {
        String seqJson = task.getSyncTaskTargetSeqs();
        if (seqJson == null || seqJson.isBlank()) {
            return;
        }
        Map<String, String> seqs;
        try {
            seqs = objectMapper.readValue(seqJson, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            // 存量水位坏了：直接清空，让所有目标按首跑处理，比留着一个读不懂的串更安全
            log.warn("[sync-task] 多目标水位解析失败，按首跑处理, taskId={}", task.getId(), e);
            task.setSyncTaskTargetSeqs(null);
            return;
        }
        if (seqs == null || seqs.isEmpty()) {
            return;
        }
        Map<String, List<String>> previous = previousTargetIncFields(previousJson);
        boolean changed = false;
        for (SyncTargetDTO target : targets) {
            String key = seqKey(target.targetDatasourceId(), target.targetTable());
            if (!seqs.containsKey(key)) {
                continue;
            }
            List<String> before = previous.getOrDefault(key, List.of());
            List<String> now = target.incFields() == null ? List.of() : target.incFields();
            if (!before.equals(now)) {
                seqs.remove(key);
                changed = true;
            }
        }
        if (changed) {
            task.setSyncTaskTargetSeqs(seqs.isEmpty() ? null : toJson(seqs));
        }
    }

    /**
     * 从库里存量的多目标 JSON 取「每个目标当时的增量字段」，用于判断字段是否变过。
     *
     * @param json 存量多目标 JSON，可为空
     * @return 水位键 → 增量字段列表
     */
    private Map<String, List<String>> previousTargetIncFields(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            List<SyncTargetDTO> parsed = objectMapper.readValue(json, new TypeReference<List<SyncTargetDTO>>() { });
            if (parsed == null || parsed.isEmpty()) {
                return Map.of();
            }
            Map<String, List<String>> result = new HashMap<>();
            for (SyncTargetDTO item : parsed) {
                if (item == null || item.targetDatasourceId() == null || item.targetTable() == null) {
                    continue;
                }
                result.put(seqKey(item.targetDatasourceId(), item.targetTable()),
                        item.incFields() == null ? List.of() : item.incFields());
            }
            return result;
        } catch (Exception e) {
            log.warn("[sync-task] 存量多目标配置解析失败，水位按变化处理: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 校验并写入同步模式 / 增量 / Hook 配置。
     *
     * <p>增量字段在<b>保存时</b>就真回源核对（JDBC 取一次结果集列，HTTP 复用保存期探活
     * 已经取到的列）：字段拼错若放过去，要等下一次执行才报「列不存在」，而增量任务的
     * 执行失败还会让用户误判水位。增量与「先清空再插入」互斥也在保存时拦掉——
     * 每批执行都清一次表会把未同步的数据反复抹掉。</p>
     *
     * <p><b>全量模式不覆盖已存的增量配置</b>：切到全量可能是临时的，来回切换不该丢水位。
     * 水位只在增量字段<b>变化</b>时作废（旧字段的水位值对新字段没有意义，沿用会漏数据）。
     * 水位列本身不在入参里（见 SyncTaskDTO 的注释）：服务端执行成功后回写，界面伪造不了。</p>
     *
     * @param task             目标实体（更新时已带库里的存量值）
     * @param request          入参
     * @param sourceType       已解析的输入源类型
     * @param reachableColumns HTTP 源保存期探活取到的列（非 HTTP 源为 null）
     */
    private void applyModeAndHook(MsSyncTask task, SyncTaskDTO request, SyncSourceType sourceType,
                                  List<SyncColumnVO> reachableColumns) {
        SyncMode syncMode = SyncMode.from(request.syncMode());
        task.setSyncTaskSyncMode(syncMode.code());
        if (syncMode == SyncMode.INCREMENTAL) {
            if (sourceType != SyncSourceType.JDBC && !sourceType.isHttp()) {
                throw new IllegalArgumentException("增量模式仅支持 JDBC / HTTP 输入源，文件/目录源请用全量模式");
            }
            // 多字段按选择顺序构成复合水位：顺序有意义，不能排序也不能去重后重排
            List<String> incFields = parseIncFields(request.incField());
            if (incFields.isEmpty()) {
                throw new IllegalArgumentException("增量模式必须选择增量字段");
            }
            for (String incField : incFields) {
                if (!INC_FIELD_PATTERN.matcher(incField).matches()) {
                    throw new IllegalArgumentException("增量字段名只允许字母、数字、下划线: " + incField);
                }
            }
            if (SyncWriteMode.from(request.writeMode()) == SyncWriteMode.OVERWRITE) {
                throw new IllegalArgumentException(
                        "增量模式不能使用「先清空再插入」——每批执行都会清空目标表，建议「存在即更新」或「追加插入」");
            }
            Integer batch = request.incBatchSize();
            if (batch != null && batch < 1) {
                throw new IllegalArgumentException("每批数量必须为正整数（留空表示不限）");
            }
            List<SyncColumnVO> columns;
            if (sourceType == SyncSourceType.JDBC) {
                try {
                    columns = loadSourceColumns(request.sourceDatasourceId(), request.sourceSql());
                } catch (IllegalArgumentException | IllegalStateException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalArgumentException("读取源结果集列失败: " + rootMessage(e));
                }
            } else {
                columns = reachableColumns;
            }
            requireFieldsInColumns(incFields, columns);
            String joined = String.join(",", incFields);
            String previousField = task.getSyncTaskIncField();
            task.setSyncTaskIncField(joined);
            task.setSyncTaskIncBatchSize(batch);
            if (!Objects.equals(previousField, joined)) {
                task.setSyncTaskLastSeq(null);
            }
        }
        applyHookConfig(task, request);
    }

    /**
     * 校验并写入 Hook 通知配置。
     *
     * <p>Hook 是<b>任务级</b>的旁路：多目标时一次执行只发一条完成通知（正文里带各目标的明细），
     * 不给每个目标各发一条——否则一张表失败会连带把通知次数翻倍，接收方还得自己去重。</p>
     *
     * @param task    目标实体
     * @param request 入参
     */
    private void applyHookConfig(MsSyncTask task, SyncTaskDTO request) {
        String hookUrl = request.hookUrl() == null ? "" : request.hookUrl().trim();
        if (hookUrl.isEmpty()) {
            task.setSyncTaskHookUrl(null);
        } else {
            if (!hookUrl.matches("(?i)^https?://\\S+$")) {
                throw new IllegalArgumentException("Hook 通知地址必须以 http:// 或 https:// 开头");
            }
            if (hookUrl.length() > 512) {
                throw new IllegalArgumentException("Hook 通知地址过长（最多 512 字符）");
            }
            task.setSyncTaskHookUrl(hookUrl);
        }
        task.setSyncTaskHookNotify(SyncHookNotify.from(request.hookNotify()).code());
    }

    /**
     * 校验 HTTP 源可用：保存时真打一次请求并取列。
     *
     * <p>网络源是所有源类型里最脆的——地址拼错、要鉴权、JSON 路径写错、选择器写错，
     * 存下来不校验就等于把问题推迟到下一次定时执行才炸，那时任务已经处于"启用"状态，
     * 失败原因还可能与真正的错因隔了几小时。</p>
     *
     * @param type    源类型
     * @param url     请求地址
     * @param options HTTP 选项
     * @return 探活取到的源列（供保存期增量字段核对复用，避免为校验多打一次请求）
     * @throws IllegalArgumentException 请求失败或响应里取不到列
     */
    private List<SyncColumnVO> assertHttpSourceReachable(SyncSourceType type, String url, SyncHttpOptions options) {
        try {
            List<SyncColumnVO> columns = httpColumnsOf(type, url, options);
            log.info("[sync-task] HTTP 源校验通过, type={}, url={}, columns={}", type, url, columns.size());
            return columns;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("HTTP 源校验失败: " + rootMessage(e));
        }
    }

    /**
     * 校验数据源可用于同步。
     *
     * <p>把「不存在 / 非 jdbc」这类 {@code RuntimeException} 归一成
     * {@link IllegalArgumentException}，让上层统一按业务错误返回，而不是 500。</p>
     *
     * @param datasourceId 数据源 ID
     * @param label        用途说明，用于错误定位
     */
    private void validateDatasource(Long datasourceId, String label) {
        try {
            datasourceService.jdbcInfo(datasourceId);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(label + "不可用: " + e.getMessage());
        }
    }

    /**
     * 校验源 SQL 必须是单条 SELECT。
     *
     * @param sql 源 SQL
     */
    private void validateSelectSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("源 SQL 不能为空");
        }
        if (!SyncJdbcSupport.isSelect(sql)) {
            throw new IllegalArgumentException("源 SQL 必须以 SELECT 开头（同步只读源库，不允许写语句）");
        }
    }

    /**
     * 执行前校验任务可跑性：把「缺配置」类问题在读源之前拦成一句人话。
     *
     * <p><b>为什么必须有</b>：存量行可能是补列之前建的（如 FILE 任务没有 source_path），
     * 直接 {@code Path.of(null)} 会抛 NPE——界面只剩一句 NullPointerException，
     * 用户既不知道缺什么也不知道去哪补。校验失败走 execute 的统一失败路径：
     * 记入调试日志、回写 FAILED、完成照常发 Hook，与其它业务失败同口径。</p>
     *
     * @param task 任务
     * @throws IllegalArgumentException 缺配置或模式冲突
     */
    private void validateTaskForRun(MsSyncTask task) {
        SyncSourceType type = SyncSourceType.from(task.getSyncTaskSourceType());
        if (type == SyncSourceType.JDBC) {
            if (task.getSyncTaskSourceDatasourceId() == null) {
                throw new IllegalArgumentException("未配置源数据源，请编辑任务补全");
            }
            validateSelectSql(task.getSyncTaskSourceSql());
        } else if (type == SyncSourceType.FILE) {
            String path = task.getSyncTaskSourcePath();
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("未配置源文件路径，请编辑任务补全（存量任务可能缺路径）");
            }
            if (!Files.isRegularFile(Path.of(path))) {
                throw new IllegalArgumentException("源文件不存在或不是普通文件: " + path);
            }
        } else if (type == SyncSourceType.DIR) {
            String path = task.getSyncTaskSourcePath();
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("未配置源目录路径，请编辑任务补全（存量任务可能缺路径）");
            }
            if (!Files.isDirectory(Path.of(path))) {
                throw new IllegalArgumentException("源目录不存在: " + path);
            }
        } else {
            String path = task.getSyncTaskSourcePath();
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("未配置 HTTP 请求地址，请编辑任务补全");
            }
        }
        List<SyncTargetPlan> targets = resolveTargets(task);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("未配置任何目标表，请编辑任务补全");
        }
        boolean anyIncremental = false;
        for (SyncTargetPlan plan : targets) {
            if (!plan.incremental()) {
                continue;
            }
            anyIncremental = true;
            if (type != SyncSourceType.JDBC && !type.isHttp()) {
                throw new IllegalArgumentException("增量模式仅支持 JDBC / HTTP 输入源，请编辑任务改回全量或换源类型（"
                        + plan.label() + "）");
            }
            requireIncFields(plan);
            if (SyncWriteMode.from(plan.writeMode()) == SyncWriteMode.OVERWRITE) {
                throw new IllegalArgumentException(plan.label()
                        + "：增量模式不能使用「先清空再插入」写入模式，请编辑任务调整");
            }
        }
        if (!anyIncremental) {
            // 全量目标没有水位概念，任务级残留的字段不参与执行，不必校验
            return;
        }
    }

    /**
     * 取出并校验某个目标的增量字段（可多字段，按顺序构成复合水位）。
     *
     * @param plan 目标计划
     * @return 合法的增量字段列表（保持配置顺序）
     * @throws IllegalArgumentException 未配置或含非法字符
     */
    private List<String> requireIncFields(SyncTargetPlan plan) {
        List<String> fields = plan.incFields() == null ? List.of() : plan.incFields();
        if (fields.isEmpty()) {
            throw new IllegalArgumentException(plan.label() + "：增量模式未配置增量字段，请编辑任务补全");
        }
        for (String field : fields) {
            if (!INC_FIELD_PATTERN.matcher(field).matches()) {
                throw new IllegalArgumentException(plan.label() + "：增量字段名不合法（只允许字母、数字、下划线）: " + field);
            }
        }
        return fields;
    }

    /**
     * 校验一组增量字段都在结果集列里存在（忽略大小写）。
     *
     * <p>复合水位要求每个字段都能取到值，少任何一个都会让水位链断掉，
     * 因此逐个核对、一次把缺失项全报出来，避免用户改一个跑一次。</p>
     *
     * @param incFields 增量字段（按顺序）
     * @param columns   结果集列（可为 null）
     * @throws IllegalArgumentException 有字段不存在
     */
    private static void requireFieldsInColumns(List<String> incFields, List<SyncColumnVO> columns) {
        if (incFields == null || incFields.isEmpty()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String field : incFields) {
            if (columns == null || !containsIgnoreCase(columns, field)) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("增量字段「" + String.join("、", missing) + "」不在源结果集列中"
                    + (columns == null ? "" : "：" + columnNamesOf(columns)));
        }
    }

    /**
     * 增量模式的读取 SQL（支持<b>复合水位</b>）。
     *
     * <p>多个增量字段按选择顺序构成一个字典序水位：前一个字段相等时才比较后一个。
     * 例如 {@code (created_at, id)} 的水位是 {@code (2026-10-24 10:00, 812)}，
     * 条件写成 {@code created_at > ? OR (created_at = ? AND id > ?)}，排序为
     * {@code ORDER BY created_at ASC, id ASC}。这样同一时间戳内的多条记录不会因为
     * 时间精度相同而被漏掉（单字段水位在这种情况下会丢掉除第一条以外的所有行）。</p>
     *
     * <p>为什么升序：增量按「水位 &lt; 值」取新数据，若倒序+LIMIT 会先取到最新的 N 条、
     * 把水位推到最大，夹在中间尚未同步的数据永远不会再被取到。升序取最老的 N 条、
     * 水位=本批最大值，积压才能不漏不重地分轮消化。</p>
     *
     * <p>水位值以 JSON 数组串保存在水位列里（多目标时每目标独立），字段数与水位数组长度
     * 不一致时按「无法解析」处理——宁可从旧水位重取（至少一次），也不猜一个错的比较边界。</p>
     *
     * <p>字面量规则：纯数字不加引号（按数值比较），其余单引号包裹并转义（时间戳/字符串）。
     * LIMIT 语法按 MySQL/PG/H2 通用口径（与 REPLACE/UPSERT 写模式的 MySQL 取向一致）。</p>
     *
     * @param task      任务
     * @param plan      目标计划
     * @param incFields 已校验过的增量字段（按顺序）
     * @return 包裹后的读取 SQL
     */
    private String buildIncrementalSql(MsSyncTask task, SyncTargetPlan plan, List<String> incFields) {
        String inner = task.getSyncTaskSourceSql().trim();
        if (inner.endsWith(";")) {
            inner = inner.substring(0, inner.length() - 1).trim();
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM (").append(inner).append(") __sync_inc");
        String lastSeq = plan.lastSeq();
        List<String> seqValues = parseSeqValues(lastSeq, incFields.size());
        if (!seqValues.isEmpty()) {
            sql.append(" WHERE ").append(compositeWhere(incFields, seqValues));
        }
        sql.append(" ORDER BY ")
                .append(incFields.stream().map(f -> f + " ASC").collect(java.util.stream.Collectors.joining(", ")));
        Integer batch = plan.incBatchSize();
        if (batch != null && batch > 0) {
            sql.append(" LIMIT ").append(batch);
        }
        return sql.toString();
    }

    /**
     * 复合水位的 WHERE 条件。
     *
     * <p>展开成「前 n 个字段都相等且第 n+1 个更大」的若干分支，避免依赖
     * {@code (a,b) > (?,?)} 这种行值比较——Oracle 不支持，MySQL 8 才有稳定行为。</p>
     *
     * @param incFields 增量字段（按顺序）
     * @param values    与字段一一对应的水位值
     * @return WHERE 条件（不含 {@code WHERE} 关键字）
     */
    private static String compositeWhere(List<String> incFields, List<String> values) {
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < incFields.size(); i++) {
            if (i > 0) {
                where.append(" OR ");
            }
            where.append("(");
            for (int j = 0; j < i; j++) {
                where.append(incFields.get(j)).append(" = ").append(seqLiteral(values.get(j))).append(" AND ");
            }
            where.append(incFields.get(i)).append(" > ").append(seqLiteral(values.get(i))).append(")");
        }
        return where.toString();
    }

    /**
     * 把复合水位串解析成与字段一一对应的值列表。
     *
     * <p>兼容两种历史形态：单字段的裸值（旧任务），以及多字段的 JSON 数组串。</p>
     *
     * @param lastSeq   水位串
     * @param fieldCount 增量字段数量
     * @return 值列表；无法对应时返回空列表（按首跑处理）
     */
    private static List<String> parseSeqValues(String lastSeq, int fieldCount) {
        if (lastSeq == null || lastSeq.isBlank() || fieldCount <= 0) {
            return List.of();
        }
        String value = lastSeq.trim();
        if (value.startsWith("[")) {
            try {
                List<String> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(value, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
                if (parsed == null || parsed.size() != fieldCount) {
                    return List.of();
                }
                return parsed;
            } catch (Exception e) {
                return List.of();
            }
        }
        return fieldCount == 1 ? List.of(value) : List.of();
    }

    /**
     * 把一行的多个增量字段值拼成复合水位串。
     *
     * @param values 按增量字段顺序取出的值
     * @return 水位串；全为空返回 null
     */
    private static String joinSeqValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(values);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 水位值转 SQL 字面量。
     *
     * @param seq 水位
     * @return 数字原样 / 其余单引号包裹并转义
     */
    private static String seqLiteral(String seq) {
        if (NUMERIC_SEQ_PATTERN.matcher(seq).matches()) {
            return seq;
        }
        return "'" + seq.replace("'", "''") + "'";
    }

    /**
     * 行值转水位字符串。
     *
     * @param value 行值
     * @return 去空格后的字符串；null 返回 null
     */
    private static String seqOf(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 比较两个水位字符串：双方都是纯数字时按数值比（否则 "9" > "10" 会把水位推错），
     * 否则按字典序（时间戳字符串的字典序即时间序）。
     *
     * @param a 左值
     * @param b 右值
     * @return 比较结果
     */
    private static int seqCompare(String a, String b) {
        if (NUMERIC_SEQ_PATTERN.matcher(a).matches() && NUMERIC_SEQ_PATTERN.matcher(b).matches()) {
            return new BigDecimal(a).compareTo(new BigDecimal(b));
        }
        return a.compareTo(b);
    }

    /**
     * 用本批行值滚动更新**复合**增量水位。
     *
     * <p>多个字段按字典序比较：先比第一个字段，相同时再比第二个，以此类推。
     * 只有行里所有增量字段都有值时才参与比较——缺值时无法确定它在序列中的位置，
     * 贸然推进水位会跳过中间数据。</p>
     *
     * @param max       当前最大水位（出参）
     * @param row       源行
     * @param incFields 增量字段（按顺序）
     */
    private static void trackSeqs(AtomicReference<String> max, Map<String, Object> row, List<String> incFields) {
        List<String> values = new ArrayList<>(incFields.size());
        for (String field : incFields) {
            String value = seqOf(row.get(field));
            if (value == null || value.isEmpty()) {
                return;
            }
            values.add(value);
        }
        String candidate = joinSeqValues(values);
        if (candidate == null) {
            return;
        }
        String current = max.get();
        if (current == null || compareSeqValues(current, candidate, incFields.size()) < 0) {
            max.set(candidate);
        }
    }

    /**
     * 比较两个复合水位串的大小（字典序）。
     *
     * @param left       左水位串
     * @param right      右水位串
     * @param fieldCount 字段数量
     * @return 负数表示 left &lt; right
     */
    private static int compareSeqValues(String left, String right, int fieldCount) {
        List<String> a = parseSeqValues(left, fieldCount);
        List<String> b = parseSeqValues(right, fieldCount);
        if (a.isEmpty() || b.isEmpty()) {
            return String.valueOf(left).compareTo(String.valueOf(right));
        }
        for (int i = 0; i < a.size(); i++) {
            int cmp = seqCompare(a.get(i), b.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /**
     * 推进某个目标的独立增量水位（内存 + 多目标水位 JSON 各一份）。
     *
     * <p>多目标的水位存在 {@code ms_sync_task_target_seqs} 里，键为「数据源ID#库.表」。
     * 用 JSON 而不是单列，是因为每个目标各有一套水位：A 表失败不该把 B 表的水位拖住。</p>
     *
     * @param task      任务
     * @param plan      目标计划
     * @param readCount 本批读取行数
     * @param seqMax    本批最大水位（无有效值为 null）
     */
    private void advanceTargetWatermark(MsSyncTask task, SyncTargetPlan plan, long readCount, String seqMax) {
        if (seqMax == null || seqMax.isBlank()) {
            if (readCount > 0) {
                runLogBuffer.warn(task.getId(), "[" + plan.label() + "] 本批 " + readCount
                        + " 行均无增量字段值，水位不推进");
            }
            return;
        }
        String previous = plan.lastSeq();
        if (Objects.equals(previous, seqMax)) {
            return;
        }
        String key = seqKey(plan.targetDatasourceId(), plan.targetTable());
        if (task.getSyncTaskTargets() == null || task.getSyncTaskTargets().isBlank()) {
            // 旧单目标任务：水位仍写回原来的单列，保持与旧版一致（多目标 JSON 为空即回落这条路径）
            task.setSyncTaskLastSeq(seqMax);
            taskMapper.update(null, new LambdaUpdateWrapper<MsSyncTask>()
                    .eq(MsSyncTask::getId, task.getId())
                    .set(MsSyncTask::getSyncTaskLastSeq, seqMax));
            runLogBuffer.info(task.getId(), "增量水位推进："
                    + (previous == null || previous.isBlank() ? "（首跑）" : previous) + " → " + seqMax
                    + "（本批 " + readCount + " 行）");
            return;
        }
        String json = task.getSyncTaskTargetSeqs();
        Map<String, String> seqs = new java.util.LinkedHashMap<>();
        if (json != null && !json.isBlank()) {
            try {
                Map<String, String> parsed = objectMapper.readValue(json,
                        new TypeReference<Map<String, String>>() { });
                if (parsed != null) {
                    seqs.putAll(parsed);
                }
            } catch (Exception e) {
                // 旧水位坏了不影响本次推进：以本次结果覆盖该目标键
                runLogBuffer.warn(task.getId(), "多目标水位解析失败，按本次结果覆盖: " + e.getMessage());
            }
        }
        seqs.put(key, seqMax);
        String merged;
        try {
            merged = objectMapper.writeValueAsString(seqs);
        } catch (Exception e) {
            runLogBuffer.warn(task.getId(), "多目标水位序列化失败，本次水位不落库: " + e.getMessage());
            return;
        }
        task.setSyncTaskTargetSeqs(merged);
        taskMapper.update(null, new LambdaUpdateWrapper<MsSyncTask>()
                .eq(MsSyncTask::getId, task.getId())
                .set(MsSyncTask::getSyncTaskTargetSeqs, merged));
        runLogBuffer.info(task.getId(), "[" + plan.label() + "] 增量水位推进："
                + (previous == null || previous.isBlank() ? "（首跑）" : previous) + " → " + seqMax
                + "（本批 " + readCount + " 行）");
    }

    /**
     * 给 HTTP 地址追加增量查询参数。
     *
     * @param url   原地址（可能已带查询串）
     * @param field 参数名（增量字段，已过白名单）
     * @param value 参数值（上次水位）
     * @return 带参数的地址
     */
    private static String appendQuery(String url, String field, String value) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + URLEncoder.encode(field, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 判断列集合里是否含指定列（忽略大小写）。
     *
     * @param columns 列集合
     * @param name    列名
     * @return 是否存在
     */
    private static boolean containsIgnoreCase(List<SyncColumnVO> columns, String name) {
        return columns.stream().anyMatch(column -> column.name().equalsIgnoreCase(name));
    }

    /**
     * 列名列表（错误提示用）。
     *
     * @param columns 列集合
     * @return 逗号分隔的列名
     */
    private static String columnNamesOf(List<SyncColumnVO> columns) {
        return String.join(", ", columns.stream().map(SyncColumnVO::name).toList());
    }

    /**
     * 发送完成通知并把结果写进本次执行日志（必须在 runLogBuffer.end 之前调用，
     * end 之后的日志会被静默丢弃——见 SyncTaskRunLogBuffer 的语义）。
     *
     * <p>通知失败只记 WARN，绝不影响同步结果：回调是旁路。</p>
     *
     * @param task    任务
     * @param trigger 触发方式
     * @param result  执行结果
     */
    private void sendDoneHook(MsSyncTask task, String trigger, SyncTaskRunVO result) {
        try {
            String outcome = hookNotifier.notifyDone(task, trigger, result);
            if (outcome == null) {
                return;
            }
            if (outcome.startsWith("失败") || outcome.contains("非 2xx")) {
                runLogBuffer.warn(task.getId(), "完成通知 → " + task.getSyncTaskHookUrl() + "：" + outcome);
            } else {
                runLogBuffer.info(task.getId(), "完成通知 → " + task.getSyncTaskHookUrl() + "：" + outcome);
            }
        } catch (Exception e) {
            runLogBuffer.warn(task.getId(), "完成通知异常：" + rootMessage(e));
        }
    }

    /**
     * 校验定时表达式。
     *
     * @param cron 表达式，可为 空
     */
    private void validateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            return;
        }
        try {
            new CronTrigger(cron.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("定时表达式不合法: " + cron);
        }
    }

    /**
     * 规范化字段映射 JSON，空值统一存 空。
     *
     * @param json 原始 JSON
     * @return 规范化后的 JSON，无映射时为 空
     */
    private String normalizeMapping(String json) {
        List<SyncFieldMappingVO> mapping = parseMapping(json);
        if (mapping.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(mapping);
        } catch (Exception e) {
            throw new IllegalStateException("字段映射序列化失败: " + e.getMessage());
        }
    }

    /**
     * 解析字段映射 JSON。
     *
     * @param json JSON，可为 空
     * @return 字段映射列表
     */
    private List<SyncFieldMappingVO> parseMapping(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<SyncFieldMappingVO> mapping = objectMapper.readValue(json, new TypeReference<List<SyncFieldMappingVO>>() { });
            return mapping == null ? List.of() : mapping;
        } catch (Exception e) {
            throw new IllegalArgumentException("字段映射格式不合法: " + e.getMessage());
        }
    }

    /**
     * 从任务解析出**生效的目标列表**（多目标，或旧单目标回落）。
     *
     * <p>旧库的 {@code ms_sync_task_targets} 为空时，用任务原有的单目标三件套
     * （数据源 / 表 / 写入模式）构造一个目标，因此**存量任务不需要迁移**就照旧执行。</p>
     *
     * <p>多目标 JSON 里每项都自带写入模式、同步模式、增量字段、映射与批量，
     * 这正是「每个目标都是现在的这些配置」的落地形式；顶层同名字段只在
     * 旧单目标回落时使用。</p>
     *
     * @param task 任务
     * @return 目标列表（至少 1 项；未配置返回空列表）
     */
    private List<SyncTargetPlan> resolveTargets(MsSyncTask task) {
        String json = task.getSyncTaskTargets();
        if (json != null && !json.isBlank()) {
            List<SyncTargetDTO> parsed;
            try {
                parsed = objectMapper.readValue(json, new TypeReference<List<SyncTargetDTO>>() { });
            } catch (Exception e) {
                throw new IllegalArgumentException("多目标配置格式不合法: " + e.getMessage());
            }
            if (parsed != null && !parsed.isEmpty()) {
                List<SyncTargetPlan> plans = new ArrayList<>(parsed.size());
                for (SyncTargetDTO item : parsed) {
                    if (item == null || item.targetDatasourceId() == null
                            || item.targetTable() == null || item.targetTable().isBlank()) {
                        throw new IllegalArgumentException("多目标配置里存在缺少数据源或表名的目标");
                    }
                    String mode = item.syncMode() == null || item.syncMode().isBlank()
                            ? SyncMode.FULL.name() : SyncMode.from(item.syncMode()).name();
                    List<String> incFields = item.incFields() == null ? List.of()
                            : item.incFields().stream().filter(f -> f != null && !f.isBlank())
                            .map(String::trim).distinct().toList();
                    plans.add(new SyncTargetPlan(
                            item.targetDatasourceId(),
                            item.targetTable().trim(),
                            SyncWriteMode.from(item.writeMode()).code(),
                            mode,
                            incFields,
                            item.incBatchSize(),
                            item.columnMapping(),
                            item.batchSize(),
                            findTargetSeq(task, item.targetDatasourceId(), item.targetTable())));
                }
                return plans;
            }
        }
        // 旧单目标回落：与多目标改造前的行为完全一致
        if (task.getSyncTaskTargetDatasourceId() == null
                || task.getSyncTaskTargetTable() == null || task.getSyncTaskTargetTable().isBlank()) {
            return List.of();
        }
        String mode = SyncMode.from(task.getSyncTaskSyncMode()).name();
        // 单目标任务的增量字段也是「逗号分隔的有序串」（复合水位），必须拆开而不是当成一个字段名
        List<String> incFields = parseIncFields(task.getSyncTaskIncField());
        return List.of(new SyncTargetPlan(
                task.getSyncTaskTargetDatasourceId(),
                task.getSyncTaskTargetTable().trim(),
                SyncWriteMode.from(task.getSyncTaskWriteMode()).code(),
                mode,
                incFields,
                task.getSyncTaskIncBatchSize(),
                task.getSyncTaskColumnMapping(),
                task.getSyncTaskBatchSize(),
                task.getSyncTaskLastSeq()));
    }

    /**
     * 取某个目标的独立水位。
     *
     * <p>多目标各有各的水位：同一份源数据写进两张表时，一张失败不应把另一张的水位也拖住，
     * 否则失败的那张永远停在旧水位、成功的会重复消费。水位存在多目标 JSON 的
     * {@code lastSeq} 字段里（服务端回写，入参不认）。</p>
     *
     * @param task               任务
     * @param targetDatasourceId 目标数据源 ID
     * @param targetTable        目标表名
     * @return 该目标的水位；没有返回 null
     */
    private String findTargetSeq(MsSyncTask task, Long targetDatasourceId, String targetTable) {
        String json = task.getSyncTaskTargetSeqs();
        if (json == null || json.isBlank() || targetTable == null) {
            return null;
        }
        try {
            Map<String, String> seqs = objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
            return seqs == null ? null : seqs.get(seqKey(targetDatasourceId, targetTable));
        } catch (Exception e) {
            // 水位只是进度记录，格式坏了不能让整次执行失败；记录后按无水位（首跑）处理
            log.warn("[sync-task] 多目标水位解析失败，按无水位处理, taskId={}", task.getId(), e);
            return null;
        }
    }

    /**
     * 水位键：数据源 + 表名。
     *
     * @param targetDatasourceId 目标数据源 ID
     * @param targetTable        目标表名
     * @return 水位键
     */
    private static String seqKey(Long targetDatasourceId, String targetTable) {
        return targetDatasourceId + "#" + (targetTable == null ? "" : targetTable.trim());
    }

    /**
     * 解析输入源选项 JSON（统一归一化，空值走默认）。
     *
     * @param json JSON，可为 空
     * @return 归一化后的选项
     */
    private SyncSourceOptions parseSourceOptions(String json) {
        if (json == null || json.isBlank()) {
            return SyncSourceOptions.defaults().normalized();
        }
        try {
            SyncSourceOptions parsed = objectMapper.readValue(json, SyncSourceOptions.class);
            return (parsed == null ? SyncSourceOptions.defaults() : parsed).normalized();
        } catch (Exception e) {
            throw new IllegalArgumentException("输入源选项格式不合法: " + e.getMessage());
        }
    }

    /**
     * 解析 HTTP 源选项 JSON。
     *
     * @param json JSON，可为 空
     * @return 归一化后的选项
     */
    private SyncHttpOptions parseHttpOptions(String json) {
        if (json == null || json.isBlank()) {
            return SyncHttpOptions.defaults().normalized();
        }
        try {
            SyncHttpOptions parsed = objectMapper.readValue(json, SyncHttpOptions.class);
            return (parsed == null ? SyncHttpOptions.defaults() : parsed).normalized();
        } catch (Exception e) {
            throw new IllegalArgumentException("HTTP 输入源选项格式不合法: " + e.getMessage());
        }
    }

    /**
     * 取任务上的输入源选项（按源类型分派：网络族用 HTTP 选项，文件族用文件选项）。
     *
     * @param task 任务
     * @return 归一化后的 HTTP 选项（非 HTTP 源时为全默认值）
     */
    private SyncHttpOptions httpOptionsOf(MsSyncTask task) {
        if (!SyncSourceType.from(task.getSyncTaskSourceType()).isHttp()) {
            return SyncHttpOptions.defaults().normalized();
        }
        return parseHttpOptions(task.getSyncTaskSourceOptions());
    }

    /**
     * 取任务上的文件选项（按源类型分派）。
     *
     * @param task 任务
     * @return 归一化后的文件选项（非文件族时为全默认值）
     */
    private SyncSourceOptions optionsOf(MsSyncTask task) {
        if (!SyncSourceType.from(task.getSyncTaskSourceType()).isFile()) {
            return SyncSourceOptions.defaults().normalized();
        }
        return parseSourceOptions(task.getSyncTaskSourceOptions());
    }

    /**
     * 对象转 JSON。
     *
     * @param value 对象
     * @return JSON 字符串
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("输入源选项序列化失败: " + e.getMessage());
        }
    }

    /**
     * 文件表头转源列（文件源的「源列」就是表头）。
     *
     * @param file    文件
     * @param options 选项
     * @return 源列
     */
    private List<SyncColumnVO> headersOf(Path file, SyncSourceOptions options) {
        List<String> headers = MonitorFileRowsSupport.headers(file, options);
        if (headers.isEmpty()) {
            throw new IllegalStateException("文件没有可用表头: " + file);
        }
        return headers.stream().map(name -> new SyncColumnVO(name, "string", "")).toList();
    }

    /**
     * 解析本次执行的输入源列。
     *
     * <p>JDBC 走结果集元数据；文件/目录走文件表头；HTTP 走真实请求一次取列。
     * 目录模式取首个匹配文件当样本——同一目录下的文件应保持同一套表头，否则字段映射无法统一。</p>
     *
     * @param task 任务
     * @return 源列
     * @throws Exception 源不可用
     */
    private List<SyncColumnVO> resolveSourceColumns(MsSyncTask task) throws Exception {
        SyncSourceType type = SyncSourceType.from(task.getSyncTaskSourceType());
        if (type == SyncSourceType.JDBC) {
            return loadSourceColumns(task.getSyncTaskSourceDatasourceId(), task.getSyncTaskSourceSql());
        }
        if (type.isHttp()) {
            return httpColumnsOf(type, task.getSyncTaskSourcePath(), httpOptionsOf(task));
        }
        SyncSourceOptions options = optionsOf(task);
        Path sample = type == SyncSourceType.FILE
                ? Path.of(task.getSyncTaskSourcePath())
                : listFiles(Path.of(task.getSyncTaskSourcePath()), options).stream().findFirst().orElse(null);
        if (sample == null) {
            throw new IllegalStateException("目录下没有匹配 " + options.pattern() + " 的文件: " + task.getSyncTaskSourcePath());
        }
        return headersOf(sample, options);
    }

    /**
     * HTTP 源列：真实请求一次取列名。
     *
     * <p>不用「配置里手填列名」的做法——接口返回什么列是外部决定的，猜错就会静默丢数据；
     * 预览时打一次真实请求换来准确列名，比事后排查划算。</p>
     *
     * @param type    源类型
     * @param url     请求地址
     * @param options 选项
     * @return 源列
     * @throws Exception 请求失败或响应无法解析
     */
    private List<SyncColumnVO> httpColumnsOf(SyncSourceType type, String url, SyncHttpOptions options) throws Exception {
        List<String> headers = MonitorHttpRowsSupport.headers(type, url, options);
        if (headers.isEmpty()) {
            throw new IllegalStateException("HTTP 响应里没有解析到任何列: " + url);
        }
        return headers.stream().map(name -> new SyncColumnVO(name, "string", "")).toList();
    }

    /**
     * 列出目录下匹配的数据文件。
     *
     * <p>归档子目录 {@code done/} 会被排除：它与源文件同处一个目录，递归模式下若不排除，
     * 「处理完归档」的文件下一轮会被再次同步，形成自增回环。</p>
     *
     * @param dir     目录
     * @param options 选项
     * @return 文件列表（按文件名排序，顺序稳定）
     * @throws Exception 遍历失败
     */
    private List<Path> listFiles(Path dir, SyncSourceOptions options) throws Exception {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        int depth = options.normalized().recursive() ? Integer.MAX_VALUE : 1;
        Path doneDir = dir.resolve("done");
        try (Stream<Path> stream = Files.walk(dir, depth)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(doneDir))
                    .filter(options::matches)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    /**
     * 目录模式：单个文件处理完后的动作（保留 / 归档到 done/ / 删除）。
     *
     * @param task    任务
     * @param file    已处理成功的文件
     * @param options 选项
     */
    private void afterProcess(MsSyncTask task, Path file, SyncSourceOptions options) {
        String action = options.normalized().afterProcess();
        try {
            if (SyncSourceOptions.AFTER_DELETE.equals(action)) {
                Files.deleteIfExists(file);
            } else if (SyncSourceOptions.AFTER_ARCHIVE.equals(action)) {
                Path doneDir = file.getParent().resolve("done");
                Files.createDirectories(doneDir);
                Files.move(file, doneDir.resolve(file.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            log.warn("[sync-task] 处理完动作失败, task={}, file={}, message={}", task.getId(), file, e.getMessage());
        }
    }

    /**
     * 规范化批量大小。
     *
     * @param value 入参
     * @return 批量大小
     */
    private int batchSizeOf(Integer value) {
        if (value == null || value < 1) {
            return DEFAULT_BATCH_SIZE;
        }
        return value;
    }

    /**
     * 回写执行结果到任务行。
     *
     * @param taskId 任务 ID
     * @param result 执行结果
     */
    private void persistRunResult(Long taskId, SyncTaskRunVO result) {
        try {
            var update = new LambdaUpdateWrapper<MsSyncTask>()
                    .eq(MsSyncTask::getId, taskId)
                    .set(MsSyncTask::getSyncTaskLastRunTime, result.runTime())
                    .set(MsSyncTask::getSyncTaskLastStatus, result.status())
                    .set(MsSyncTask::getSyncTaskLastReadCount, result.readCount())
                    .set(MsSyncTask::getSyncTaskLastWriteCount, result.writeCount())
                    .set(MsSyncTask::getSyncTaskLastCostMs, result.costMs())
                    .set(MsSyncTask::getSyncTaskLastMessage, truncate(result.message()));
            taskMapper.update(null, update);
        } catch (Exception e) {
            log.warn("[sync-task] 回写执行结果失败, id={}, message={}", taskId, e.getMessage());
        }
    }

    /**
     * 排定或取消某个任务的定时。
     *
     * @param task 任务
     */
    private void schedule(MsSyncTask task) {
        Long id = task.getId();
        cancelSchedule(id);
        if (id == null || scheduler == null) {
            return;
        }
        boolean enabled = task.getSyncTaskEnabled() != null && task.getSyncTaskEnabled() == 1;
        String cron = task.getSyncTaskCron();
        if (enabled && cron != null && !cron.isBlank()) {
            try {
                ScheduledFuture<?> future = scheduler.schedule(() -> triggerScheduled(id), new CronTrigger(cron.trim()));
                if (future != null) {
                    scheduledTasks.put(id, future);
                    log.info("[sync-task] 任务 {} 已排定定时: {}", id, cron);
                }
            } catch (Exception e) {
                log.warn("[sync-task] 任务 {} 排定定时失败: {}", id, e.getMessage());
            }
        }
        // 目录源：启用后挂目录监听，实现新文件实时录入（可与 cron 共存：cron 负责补扫，监听负责实时）
        watchDir(task);
    }

    /**
     * 挂载 / 卸载目录监听。
     *
     * <p>只有「目录源 + 已启用」才监听；其余情况先卸载，保证改任务后不会残留旧目录的监听。</p>
     *
     * @param task 任务
     */
    private void watchDir(MsSyncTask task) {
        Long id = task.getId();
        stopWatch(id);
        if (id == null || SyncSourceType.from(task.getSyncTaskSourceType()) != SyncSourceType.DIR) {
            return;
        }
        if (task.getSyncTaskEnabled() == null || task.getSyncTaskEnabled() != 1) {
            return;
        }
        Path dir = Path.of(task.getSyncTaskSourcePath());
        if (!Files.isDirectory(dir)) {
            log.warn("[sync-task] 目录不存在，跳过实时监听, id={}, dir={}", id, dir);
            return;
        }
        try {
            SyncDirWatcher watcher = new SyncDirWatcher(dir, optionsOf(task), file -> ingestFile(id, file));
            watcher.start();
            dirWatchers.put(id, watcher);
        } catch (Exception e) {
            log.warn("[sync-task] 启动目录监听失败, id={}, message={}", id, e.getMessage());
        }
    }

    /**
     * 停掉某个任务的目录监听。
     *
     * @param id 任务 ID
     */
    private void stopWatch(Long id) {
        SyncDirWatcher watcher = id == null ? null : dirWatchers.remove(id);
        if (watcher != null) {
            watcher.close();
        }
    }

    /**
     * 实时录入单个文件（目录监听回调）。
     *
     * <p>与整目录执行共用同一套「表头 → 映射 → 写入 → 处理完动作」，只是范围缩到单文件；
     * 走 runningTasks 同一把锁，避免与 cron 补扫同时写同一张表。</p>
     *
     * @param taskId 任务 ID
     * @param file   已落定的文件
     */
    private void ingestFile(Long taskId, Path file) {
        MsSyncTask task = taskMapper.selectById(taskId);
        if (task == null) {
            stopWatch(taskId);
            return;
        }
        AtomicBoolean running = runningTasks.computeIfAbsent(taskId, key -> new AtomicBoolean(false));
        if (!running.compareAndSet(false, true)) {
            // 正在跑别的文件或整目录补扫；该文件会在下一轮补扫/事件里被覆盖
            log.info("[sync-task] 任务 {} 正在执行，跳过一次实时录入: {}", taskId, file.getFileName());
            return;
        }
        LocalDateTime runTime = LocalDateTime.now();
        long startNanos = System.nanoTime();
        SyncTaskRunVO result = null;
        String logStatus = SyncTaskRunVO.STATUS_FAILED;
        runLogBuffer.begin(taskId, SyncTaskRunLogVO.TRIGGER_REALTIME);
        runLogBuffer.info(taskId, "实时录入 " + file.getFileName());
        try {
            SyncSourceOptions options = optionsOf(task);
            List<SyncColumnVO> sourceColumns = headersOf(file, options);
            runLogBuffer.info(taskId, "文件表头（" + sourceColumns.size() + " 列）：" + describeColumns(sourceColumns));
            List<SyncTargetPlan> targets = resolveTargets(task);
            if (targets.isEmpty()) {
                throw new IllegalStateException("没有配置任何目标表，请编辑任务补全");
            }
            long readTotal = 0L;
            long writeTotal = 0L;
            boolean allOk = true;
            for (SyncTargetPlan plan : targets) {
                try {
                    AtomicLong readCounter = new AtomicLong();
                    long writeCount = streamAndWriteTo(task, plan, sourceColumns, readCounter, file);
                    readTotal += readCounter.get();
                    writeTotal += writeCount;
                    runLogBuffer.info(taskId, "[" + plan.label() + "] 读 " + readCounter.get()
                            + " 行 / 写 " + writeCount + " 行");
                } catch (Exception e) {
                    allOk = false;
                    runLogBuffer.error(taskId, "[" + plan.label() + "] 实时录入失败：" + rootMessage(e), e);
                    log.warn("[sync-task] 实时录入目标失败, id={}, file={}, target={}, message={}",
                            taskId, file.getFileName(), plan.label(), rootMessage(e));
                }
            }
            if (allOk) {
                result = SyncTaskRunVO.success(readTotal, writeTotal, costMs(startNanos), runTime);
                // 全部目标写成功才应用「处理完」动作：只写进一半就删/归档文件，失败的表永远补不回来
                afterProcess(task, file, options);
            } else {
                result = new SyncTaskRunVO(SyncTaskRunVO.STATUS_FAILED, readTotal, writeTotal,
                        costMs(startNanos), "部分目标写入失败（见运行日志）", runTime);
            }
            logStatus = result.status();
            runLogBuffer.info(taskId, "实时录入完成：读 " + readTotal + " 行 / 写 " + writeTotal
                    + " 行，处理完动作 " + (allOk ? options.afterProcess() : "跳过（有目标失败）"));
            log.info("[sync-task] 实时录入完成, id={}, file={}, read={}, write={}, allOk={}",
                    taskId, file.getFileName(), readTotal, writeTotal, allOk);
        } catch (Exception e) {
            result = SyncTaskRunVO.failed(0L, costMs(startNanos), rootMessage(e), runTime);
            runLogBuffer.error(taskId, "实时录入失败：" + rootMessage(e), e);
            log.warn("[sync-task] 实时录入失败, id={}, file={}, message={}", taskId, file, rootMessage(e));
        } finally {
            running.set(false);
            // 与 execute 同口径：完成通知在 end() 之前发，成功/失败都发
            if (result != null) {
                sendDoneHook(task, SyncTaskRunLogVO.TRIGGER_REALTIME, result);
            }
            runLogBuffer.end(taskId, logStatus, costMs(startNanos));
        }
        persistRunResult(taskId, result);
    }

    /**
     * 定时触发入口。
     *
     * <p>必须自己吞掉所有异常：定时任务抛出异常会被 Spring 从调度中移除，
     * 之后这个任务就再也不会被触发，且界面上看不出原因。</p>
     *
     * @param taskId 任务 ID
     */
    private void triggerScheduled(Long taskId) {
        try {
            MsSyncTask task = taskMapper.selectById(taskId);
            if (task == null) {
                cancelSchedule(taskId);
                return;
            }
            execute(task, SyncTaskRunLogVO.TRIGGER_SCHEDULED);
        } catch (Exception e) {
            log.error("[sync-task] 定时执行异常, id={}", taskId, e);
        }
    }

    /**
     * 取消某个任务的定时。
     *
     * @param taskId 任务 ID
     */
    private void cancelSchedule(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
        stopWatch(taskId);
    }

    /**
     * 取任务或抛错。
     *
     * @param id 任务 ID
     * @return 任务
     */
    private MsSyncTask requireTask(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("任务 ID 不能为空");
        }
        MsSyncTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalStateException("同步任务不存在: " + id);
        }
        return task;
    }

    /**
     * 计算耗时。
     *
     * @param startNanos 起始纳秒
     * @return 毫秒
     */
    private long costMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 取根因消息。
     *
     * @param throwable 异常
     * @return 最内层可读消息
     */
    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        return truncate(message);
    }

    /**
     * 截断消息到字段长度上限。
     *
     * @param message 原消息，可为 空
     * @return 截断后的消息
     */
    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, MESSAGE_MAX_LENGTH);
    }

    /**
     * 拼列清单供调试日志展示（最多 50 列，超出以省略号收尾）。
     *
     * @param columns 源列
     * @return {@code 列名, 列名, …}
     */
    private static String describeColumns(List<SyncColumnVO> columns) {
        StringBuilder desc = new StringBuilder();
        int shown = 0;
        for (SyncColumnVO column : columns) {
            if (shown == 50) {
                desc.append('…');
                break;
            }
            if (shown > 0) {
                desc.append(", ");
            }
            desc.append(column.name());
            shown++;
        }
        return desc.toString();
    }

    /**
     * 拼字段映射清单供调试日志展示（最多 30 项，超出以省略号收尾）。
     *
     * @param mapping 字段映射
     * @return {@code 源列→目标列, …}
     */
    private static String describeMapping(List<SyncFieldMappingVO> mapping) {
        if (mapping.isEmpty()) {
            return "（空）";
        }
        StringBuilder desc = new StringBuilder();
        int shown = 0;
        for (SyncFieldMappingVO item : mapping) {
            if (shown == 30) {
                desc.append('…');
                break;
            }
            if (shown > 0) {
                desc.append(", ");
            }
            desc.append(item.source()).append('→').append(item.target());
            shown++;
        }
        return desc.toString();
    }
}