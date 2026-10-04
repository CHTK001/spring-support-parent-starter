package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
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
import com.chua.starter.monitor.support.datasync.SyncHttpOptions;
import com.chua.starter.monitor.support.datasync.SyncJdbcSupport;
import com.chua.starter.monitor.support.datasync.SyncSourceOptions;
import com.chua.starter.monitor.support.datasync.SyncSourceType;
import com.chua.starter.monitor.support.datasync.SyncWriteMode;
import com.chua.starter.monitor.support.entity.MsSyncTask;
import com.chua.starter.monitor.support.mapper.MsSyncTaskMapper;
import com.chua.starter.monitor.support.model.SyncColumnVO;
import com.chua.starter.monitor.support.model.SyncFieldMappingVO;
import com.chua.starter.monitor.support.model.SyncPreviewVO;
import com.chua.starter.monitor.support.model.SyncTaskRunVO;
import com.chua.starter.monitor.support.model.dto.SyncPreviewDTO;
import com.chua.starter.monitor.support.model.dto.SyncTaskDTO;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
     * 结果说明字段的最大长度（与 ms_sync_task_last_message 的 512 留出余量）
     */
    private static final int MESSAGE_MAX_LENGTH = 500;

    /**
     * 任务映射器
     */
    private final MsSyncTaskMapper taskMapper;

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
     * 启动时恢复定时任务。
     *
     * <p>建表由 Flyway 在上下文启动前完成；这里仍做兜底：表还没建好时只记警告，
     * 不让整个应用因为「定时任务表不存在」而起不来。</p>
     */
    @PostConstruct
    public void initScheduler() {
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
            return ReturnResult.ok(execute(task));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[sync-task] 执行同步任务失败, id={}", id, e);
            return ReturnResult.error("执行同步任务失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SyncPreviewVO> preview(SyncPreviewDTO request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("预览参数不能为空");
            }
            List<SyncColumnVO> sourceColumns = resolvePreviewSourceColumns(request);
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

    /**
     * 执行一次同步：读源、按映射转换、写目标，并回写任务状态。
     *
     * <p>执行本身不抛异常给调用方，而是把失败编码进返回值：同步任务失败是**业务结果**而不是
     * 接口故障，界面需要看到「读了几行、卡在哪一步」，用异常会只剩一句错误。</p>
     *
     * @param task 任务
     * @return 执行结果
     */
    private SyncTaskRunVO execute(MsSyncTask task) {
        Long taskId = task.getId();
        AtomicBoolean running = runningTasks.computeIfAbsent(taskId, key -> new AtomicBoolean(false));
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("任务正在执行中，请等本次结束");
        }
        LocalDateTime runTime = LocalDateTime.now();
        long startNanos = System.nanoTime();
        long readCount = 0L;
        SyncTaskRunVO result;
        try {
            SyncSourceType sourceType = SyncSourceType.from(task.getSyncTaskSourceType());
            if (sourceType == SyncSourceType.DIR) {
                // 目录模式：逐个文件独立映射与写入，单文件失败不影响其余文件
                result = executeDirectory(task, runTime, startNanos);
            } else {
                List<SyncColumnVO> sourceColumns = resolveSourceColumns(task);
                List<SyncFieldMappingVO> mapping = resolveMapping(task, sourceColumns);
                AtomicLong readCounter = new AtomicLong();
                Path file = sourceType == SyncSourceType.FILE ? Path.of(task.getSyncTaskSourcePath()) : null;
                long writeCount = streamAndWrite(task, mapping, sourceColumns, readCounter, file);
                readCount = readCounter.get();
                result = SyncTaskRunVO.success(readCount, writeCount, costMs(startNanos), runTime);
            }
        } catch (Exception e) {
            String message = rootMessage(e);
            log.error("[sync-task] 任务执行失败, id={}, message={}", taskId, message, e);
            result = SyncTaskRunVO.failed(readCount, costMs(startNanos), message, runTime);
        } finally {
            running.set(false);
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
            return SyncTaskRunVO.failed(0L, costMs(startNanos), rootMessage(e), runTime);
        }
        if (files.isEmpty()) {
            // 空目录不是错误：实时录入场景下一次触发可能刚好没有待处理文件
            return SyncTaskRunVO.success(0L, 0L, costMs(startNanos), runTime);
        }
        for (Path file : files) {
            try {
                List<SyncColumnVO> sourceColumns = headersOf(file, options);
                List<SyncFieldMappingVO> mapping = resolveMapping(task, sourceColumns);
                AtomicLong readCounter = new AtomicLong();
                long writeCount = streamAndWrite(task, mapping, sourceColumns, readCounter, file);
                readTotal += readCounter.get();
                writeTotal += writeCount;
                okCount++;
                afterProcess(task, file, options);
            } catch (Exception e) {
                failedCount++;
                if (problems.length() < 300) {
                    problems.append(file.getFileName()).append(": ").append(rootMessage(e)).append("; ");
                }
                log.warn("[sync-task] 文件同步失败, task={}, file={}, message={}", task.getId(), file, rootMessage(e));
            }
        }
        if (failedCount == 0) {
            return SyncTaskRunVO.success(readTotal, writeTotal, costMs(startNanos), runTime);
        }
        String message = "处理 " + files.size() + " 个文件，成功 " + okCount + " / 失败 " + failedCount + "：" + problems;
        return SyncTaskRunVO.failed(readTotal, costMs(startNanos), message, runTime);
    }

    /**
     * 预览用：按输入源类型取源列。
     *
     * <p>与执行路径同源（{@link #resolveSourceColumns} 也是这套分支），避免「预览能配、执行时报错」。
     * 差别只有一点：预览拿不到任务实体，用的是请求里的临时参数。</p>
     *
     * @param request 预览入参
     * @return 源列
     * @throws Exception 源不可用
     */
    private List<SyncColumnVO> resolvePreviewSourceColumns(SyncPreviewDTO request) throws Exception {
        SyncSourceType type = SyncSourceType.from(request.sourceType());
        if (type == SyncSourceType.JDBC) {
            validateSelectSql(request.sourceSql());
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
     * 决定本次执行使用的字段映射。
     *
     * <p>优先用任务里存好的映射；为空时按「同名忽略大小写」自动配对。配对结果再回源列做一次
     * 存在性校验：映射引用了不存在的源列时若放过去，写入的会是整列 空，属于最难排查的一类错。</p>
     *
     * @param task          任务
     * @param sourceColumns 源结果集列
     * @return 字段映射
     * @throws Exception 读取目标表结构失败
     */
    private List<SyncFieldMappingVO> resolveMapping(MsSyncTask task, List<SyncColumnVO> sourceColumns) throws Exception {
        List<SyncFieldMappingVO> mapping = parseMapping(task.getSyncTaskColumnMapping());
        if (mapping.isEmpty()) {
            mapping = autoMatch(sourceColumns, loadTargetColumns(task.getSyncTaskTargetDatasourceId(), task.getSyncTaskTargetTable()));
        }
        if (mapping.isEmpty()) {
            throw new IllegalStateException("字段映射为空：源列与目标列没有可配对项，请先在「字段映射」里配置");
        }
        Set<String> sourceNames = SyncJdbcSupport.columnNames(sourceColumns);
        for (SyncFieldMappingVO item : mapping) {
            if (!sourceNames.contains(item.source())) {
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
    private long streamAndWrite(MsSyncTask task, List<SyncFieldMappingVO> mapping,
                                List<SyncColumnVO> sourceColumns, AtomicLong readCounter, Path file) throws Exception {
        String[] columnNames = sourceColumns.stream().map(SyncColumnVO::name).toArray(String[]::new);
        List<DataSyncFieldMapping> engineMappings = new ArrayList<>(mapping.size());
        for (SyncFieldMappingVO item : mapping) {
            engineMappings.add(new SimpleFieldMapping(item.source(), item.target(), null));
        }
        JdbcDataSyncAgentSource jdbcSource = null;
        SyncSourceType type = SyncSourceType.from(task.getSyncTaskSourceType());
        Flux<Map<String, Object>> rows;
        if (type == SyncSourceType.JDBC) {
            // JDBC：引擎的 JDBC 源
            DatasourceJdbcInfo sourceInfo = datasourceService.jdbcInfo(task.getSyncTaskSourceDatasourceId());
            jdbcSource = new JdbcDataSyncAgentSource(
                    "sync-src-" + task.getId(), "sync-in-" + task.getId(),
                    sourceInfo.url(), sourceInfo.username(), sourceInfo.password(),
                    task.getSyncTaskSourceSql(), columnNames);
            rows = jdbcSource.read(new HashMap<>());
        } else if (type.isHttp()) {
            // HTTP-JSON / HTTP-HTML：真实请求一次，响应解析成行
            rows = MonitorHttpRowsSupport.read(type, task.getSyncTaskSourcePath(), httpOptionsOf(task));
        } else {
            // 文件：csv 走引擎定界符源，excel 走 POI（见 MonitorFileRowsSupport）
            rows = MonitorFileRowsSupport.read(file, optionsOf(task));
        }
        try {
            // 引擎的源以 Flux 暴露数据；params 不含 offset 即全量读
            Flux<Map<String, Object>> mapped = rows
                    .doOnNext(row -> readCounter.incrementAndGet())
                    .map(row -> fieldMappingConverter.applyMappings(row, engineMappings));
            return buildSink(task, mapping).writeStream(mapped);
        } finally {
            if (jdbcSource != null) {
                jdbcSource.close();
            }
        }
    }

    /**
     * 按任务配置构造目标 Sink。
     *
     * @param task    任务
     * @param mapping 字段映射（目标列顺序决定占位符顺序）
     * @return 目标 Sink
     * @throws Exception 目标数据源不可用或目标表名非法
     */
    private MonitorJdbcSyncSink buildSink(MsSyncTask task, List<SyncFieldMappingVO> mapping) throws Exception {
        List<String> targetColumns = mapping.stream().map(SyncFieldMappingVO::target).toList();
        DatasourceJdbcInfo targetInfo = datasourceService.jdbcInfo(task.getSyncTaskTargetDatasourceId());
        String quotedTable = SyncJdbcSupport.quoteTable(task.getSyncTaskTargetTable());
        SyncWriteMode mode = SyncWriteMode.from(task.getSyncTaskWriteMode());
        String writeSql = buildWriteSql(mode, quotedTable, targetColumns);
        return new MonitorJdbcSyncSink(
                "sync-sink-" + task.getId(), targetInfo, writeSql, targetColumns,
                batchSize(task), quotedTable, mode == SyncWriteMode.OVERWRITE);
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
        }
    }

    /**
     * 读取目标表的列定义。
     *
     * @param datasourceId 目标数据源 ID
     * @param table        目标表名
     * @return 列列表
     * @throws Exception 数据源不可用或表不存在
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
        if (request.targetDatasourceId() == null) {
            throw new IllegalArgumentException("目标数据源不能为空");
        }
        // 输入源按类型校验：JDBC 用数据源 + SELECT；FILE/DIR 用服务器本地绝对路径；
        // HTTP_JSON/HTTP_HTML 用 http(s) 地址 + 各自选项，并在**保存时**就真打一次请求确认能取到列——
        // 网络源最容易配错（地址拼错、需要鉴权、选择器写错），存下来不校验等于把问题留到定时执行时才炸
        SyncSourceType sourceType = SyncSourceType.from(request.sourceType());
        String sourcePath = request.sourcePath() == null ? null : request.sourcePath().trim();
        SyncSourceOptions sourceOptions = SyncSourceOptions.defaults();
        SyncHttpOptions httpOptions = SyncHttpOptions.defaults().normalized();
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
            assertHttpSourceReachable(sourceType, sourcePath, httpOptions);
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
        if (request.targetTable() == null || request.targetTable().isBlank()) {
            throw new IllegalArgumentException("目标表名不能为空");
        }
        // 提前跑一次白名单校验，别等执行时才拒
        SyncJdbcSupport.quoteTable(request.targetTable());
        validateDatasource(request.targetDatasourceId(), "目标数据源");
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

        task.setSyncTaskName(request.name().trim());
        task.setSyncTaskSourceType(sourceType.name());
        task.setSyncTaskSourceDatasourceId(sourceType == SyncSourceType.JDBC ? request.sourceDatasourceId() : null);
        task.setSyncTaskSourceSql(sourceType == SyncSourceType.JDBC ? request.sourceSql().trim() : null);
        task.setSyncTaskSourcePath(sourceType == SyncSourceType.JDBC ? null : sourcePath);
        task.setSyncTaskSourceOptions(sourceType == SyncSourceType.JDBC ? null
                : (sourceType.isHttp() ? toJson(httpOptions) : toJson(sourceOptions)));
        task.setSyncTaskTargetDatasourceId(request.targetDatasourceId());
        task.setSyncTaskTargetTable(request.targetTable().trim());
        task.setSyncTaskColumnMapping(normalizeMapping(request.columnMapping()));
        task.setSyncTaskWriteMode(SyncWriteMode.from(request.writeMode()).code());
        task.setSyncTaskBatchSize(batchSizeOf(request.batchSize()));
        task.setSyncTaskCron(request.cron() == null || request.cron().isBlank() ? null : request.cron().trim());
        task.setSyncTaskEnabled(request.enabled() == null ? 0 : (request.enabled() == 1 ? 1 : 0));
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
     * @throws IllegalArgumentException 请求失败或响应里取不到列
     */
    private void assertHttpSourceReachable(SyncSourceType type, String url, SyncHttpOptions options) {
        try {
            List<SyncColumnVO> columns = httpColumnsOf(type, url, options);
            log.info("[sync-task] HTTP 源校验通过, type={}, url={}, columns={}", type, url, columns.size());
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
     * 取任务批量大小。
     *
     * @param task 任务
     * @return 批量大小
     */
    private int batchSize(MsSyncTask task) {
        return batchSizeOf(task.getSyncTaskBatchSize());
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
        SyncTaskRunVO result;
        try {
            SyncSourceOptions options = optionsOf(task);
            List<SyncColumnVO> sourceColumns = headersOf(file, options);
            List<SyncFieldMappingVO> mapping = resolveMapping(task, sourceColumns);
            AtomicLong readCounter = new AtomicLong();
            long writeCount = streamAndWrite(task, mapping, sourceColumns, readCounter, file);
            result = SyncTaskRunVO.success(readCounter.get(), writeCount, costMs(startNanos), runTime);
            afterProcess(task, file, options);
            log.info("[sync-task] 实时录入完成, id={}, file={}, read={}, write={}",
                    taskId, file.getFileName(), readCounter.get(), writeCount);
        } catch (Exception e) {
            result = SyncTaskRunVO.failed(0L, costMs(startNanos), rootMessage(e), runTime);
            log.warn("[sync-task] 实时录入失败, id={}, file={}, message={}", taskId, file, rootMessage(e));
        } finally {
            running.set(false);
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
            execute(task);
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
}