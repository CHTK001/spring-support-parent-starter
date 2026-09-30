package com.chua.starter.job.support.manage;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.task.script.ScriptFlow;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.cluster.LeaderElector;
import com.chua.starter.job.support.dispatch.JobQuery;
import com.chua.starter.job.support.dispatch.JobRuntime;
import com.chua.starter.job.support.dispatch.JobSpec;
import com.chua.starter.job.support.execute.ScriptGlueHandlers;
import com.chua.starter.job.support.handler.BeanJobHandler;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.JobLogQuery;
import com.chua.starter.job.support.log.JobLogRecord;
import com.chua.starter.job.support.log.JobLogRepository;
import com.chua.starter.job.support.manage.dto.JobScheduleDTO;
import com.chua.starter.job.support.manage.dto.JobSpecDTO;
import com.chua.starter.job.support.manage.dto.JobStatusDTO;
import com.chua.starter.job.support.manage.vo.JobExecutorVO;
import com.chua.starter.job.support.manage.vo.JobLogContentVO;
import com.chua.starter.job.support.manage.vo.JobLogVO;
import com.chua.starter.job.support.manage.vo.JobNodeVO;
import com.chua.starter.job.support.manage.vo.JobRuntimeVO;
import com.chua.starter.job.support.manage.vo.JobSummaryVO;
import com.chua.starter.job.support.operate.JobOperator;
import com.chua.starter.job.support.sync.GlueSyncService;
import com.chua.starter.job.support.sync.RemoteHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

/**
 * 任务治理的公共实现 (与传输层无关)。
 *
 * <p>调度面有两个可选出口, 二者只做鉴权与路径绑定, 语义全部落在本类:</p>
 * <ul>
 *   <li>{@link JobManageController} —— {@code /v2/job/**}, 走平台管理员鉴权, 供接入统一认证的宿主;</li>
 *   <li>宿主自己的控制台出口 —— 带 {@code @Ignore} 绕开统一认证过滤器, 由宿主控制台会话把关
 *       (采集宿主即 {@code CollectSchedulerConsoleController})。</li>
 * </ul>
 *
 * <p>拆这一层的目的是让两种认证模型共用同一份映射与校验, 避免出现第二套调度账本口径。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class JobGovernanceService {

    /**
     * 统计「最近失败」时回溯的时间窗毫秒
     */
    private static final long FAILURE_WINDOW_MS = 24L * 60L * 60 * 1000L;

    /**
     * 单次执行日志的读取上限（字节）。
     *
     * <p>日志正文是整份读进内存再返回给浏览器的，不设上限时一个刷屏任务能把堆和响应体
     * 一起打爆；超限时取尾部，因为长任务里失败原因总在最后。</p>
     */
    private static final int MAX_LOG_CONTENT_BYTES = 512 * 1024;

    /**
     * 任务治理接口
     */
    private final JobOperator jobOperator;

    /**
     * 执行日志读取侧
     */
    private final JobLogRepository jobLogRepository;

    /**
     * 调度配置 (角色、分组、扫描参数)
     */
    private final JobProperties jobProperties;

    /**
     * 本地选主状态
     */
    private final LeaderElector jobLeaderElector;

    /**
     * 同步服务端 (仅调度中心角色装配, 其余角色取不到)
     */
    private final ObjectProvider<SyncServer> syncServerProvider;

    /**
     * 在线执行器上报的处理器清单（分布式下校正"未注册"判定）
     */
    private final RemoteHandlerRegistry remoteHandlerRegistry;

    /**
     * 脚本正文同步服务（保存/手动触发时推送正文，仅调度中心角色可取到）
     */
    private final ObjectProvider<GlueSyncService> glueSyncServiceProvider;

    /**
     * 分页查询任务运行态。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public ReturnResult<PageResult<JobRuntimeVO>> page(JobQuery query) {
        PageResult<JobRuntime> source = jobOperator.page(query);
        return ReturnResult.ok(toPage(source));
    }

    /**
     * 按分组列出任务运行态 (不分页)。
     *
     * @param group 分组, 为空列全部
     * @return 运行态列表
     */
    public ReturnResult<List<JobRuntimeVO>> list(String group) {
        List<JobRuntimeVO> rows = new ArrayList<>();
        for (JobRuntime runtime : jobOperator.list(group)) {
            rows.add(toVo(runtime));
        }
        return ReturnResult.ok(rows);
    }

    /**
     * 读取单个任务运行态。
     *
     * @param jobKey 任务键
     * @return 运行态
     */
    public ReturnResult<JobRuntimeVO> detail(String jobKey) {
        JobRuntimeVO vo = toVo(jobOperator.runtime(jobKey));
        return vo == null ? ReturnResult.error("任务不存在: " + jobKey) : ReturnResult.ok(vo);
    }

    /**
     * 运行态快照转出参，并合并在线执行器上报的处理器（本地没有不代表分布式下跑不了）。
     *
     * @param runtime 快照
     * @return 出参
     */
    private JobRuntimeVO toVo(JobRuntime runtime) {
        JobRuntimeVO vo = JobRuntimeVO.of(runtime);
        if (vo != null && !vo.isHandlerRegistered() && remoteHandlerRegistry.contains(vo.getJobKey())) {
            vo.setHandlerRegistered(true);
        }
        return vo;
    }

    /**
     * 列出当前进程可用的脚本引擎（驱动前端「片段语言」下拉）。
     *
     * <p>由 SPI 运行时枚举，装了新引擎前端自动出现，不需要改前端枚举表。</p>
     *
     * <p>统一小写后再返回：SPI 注册表归一后的扩展名是大写（如 {@code JAVA}），
     * 而本模块 {@code GlueTypes.engineOf} 与 {@code sys_job_glue_type} 的口径都是小写，
     * 两者混用会让前端下拉的「值」与「选项」对不上，所以在这里收口成引擎名。</p>
     *
     * @return 去重升序的引擎名列表
     */
    public ReturnResult<List<String>> glueEngines() {
        var engines = new TreeSet<String>();
        for (String engine : ScriptFlow.engines()) {
            if (engine != null && !engine.isBlank()) {
                engines.add(engine.trim().toLowerCase(Locale.ROOT));
            }
        }
        return ReturnResult.ok(new ArrayList<>(engines));
    }

    /**
     * 登记或更新任务 (调度配置、启停一次写入)。
     *
     * @param dto 任务规格
     * @return 是否成功
     */
    public ReturnResult<Boolean> save(JobSpecDTO dto) {
        if (dto == null) {
            return ReturnResult.illegal("任务规格不能为空");
        }
        // 脚本任务不强制任务键：生成全局唯一标识（与 DefaultJobOperator 同一规则），供后续正文推送使用
        String glueType = dto.getGlueType();
        boolean script = glueType != null && !glueType.isBlank()
                && !JobSpec.GLUE_BEAN.equalsIgnoreCase(glueType.trim());
        if (script && (dto.getJobKey() == null || dto.getJobKey().isBlank())) {
            dto.setJobKey(JobSpec.SCRIPT_JOB_KEY_PREFIX + IdUtils.createSnowflakeId());
        }
        ReturnResult<Boolean> result = execute("save", dto.getJobKey(),
                () -> jobOperator.declare(dto.toSpec()));
        // 脚本任务保存成功后立即推送正文，执行器不必等下一次派发
        if (result.isOk()) {
            pushGlue(dto.getJobKey());
        }
        return result;
    }

    /**
     * 推送脚本正文（非脚本任务/非调度中心角色时静默跳过）。
     *
     * @param jobKey 任务键
     */
    private void pushGlue(String jobKey) {
        GlueSyncService glueSyncService = glueSyncServiceProvider.getIfAvailable();
        if (glueSyncService == null || jobKey == null || jobKey.isBlank()) {
            return;
        }
        glueSyncService.push(jobKey);
    }

    /**
     * 修改调度配置并立即生效。
     *
     * @param dto 调度配置
     * @return 是否成功
     */
    public ReturnResult<Boolean> updateSchedule(JobScheduleDTO dto) {
        return execute("schedule", dto == null ? null : dto.getJobKey(),
                () -> jobOperator.updateSchedule(dto.getJobKey(), dto.getScheduleType(), dto.getScheduleTime()));
    }

    /**
     * 启停自动调度。
     *
     * @param dto 启停入参
     * @return 是否成功
     */
    public ReturnResult<Boolean> changeStatus(JobStatusDTO dto) {
        if (dto == null || dto.getEnabled() == null) {
            return ReturnResult.illegal("enabled 不能为空");
        }
        boolean enabled = dto.getEnabled();
        return execute("status", dto.getJobKey(), () -> jobOperator.changeStatus(dto.getJobKey(), enabled));
    }

    /**
     * 立即派发一次。
     *
     * <p>前端对停用任务禁用「立即触发」，后端同样把关：停用的任务不派发，避免绕过页面
     * 用接口把停用任务点起来。调度中心角色下没有在线执行器时直接拒绝——命令投出去也没人领取，
     * 静默丢失比明确报错更难排查。</p>
     *
     * @param jobKey 任务键
     * @return 是否已投递
     */
    public ReturnResult<Boolean> trigger(String jobKey) {
        if (jobKey == null || jobKey.isBlank()) {
            return ReturnResult.illegal("jobKey 不能为空");
        }
        JobRuntime runtime = jobOperator.runtime(jobKey);
        if (runtime == null) {
            return ReturnResult.error("任务不存在或未被更新: " + jobKey);
        }
        if (!runtime.isEnabled()) {
            return ReturnResult.error("任务已停用, 请先启用后再手动执行: " + jobKey);
        }
        if (jobProperties.isScheduler()) {
            SyncServer server = syncServerProvider.getIfAvailable();
            List<String> clients = server == null ? null : server.getConnectedClients();
            if (clients == null || clients.isEmpty()) {
                return ReturnResult.error("没有在线的执行器, 命令未派发: " + jobKey);
            }
        }
        // 手动触发前先推一次正文：执行器缓存缺失时也能跑起来
        pushGlue(jobKey);
        return execute("trigger", jobKey, () -> jobOperator.trigger(jobKey));
    }

    /**
     * 删除任务。
     *
     * @param jobKey 任务键
     * @return 是否成功
     */
    public ReturnResult<Boolean> remove(String jobKey) {
        ReturnResult<Boolean> result = execute("remove", jobKey, () -> jobOperator.remove(jobKey));
        if (result != null && result.isOk()) {
            // 台账行删了，执行器侧缓存的编译产物、类加载器与落盘片段也要一起释放，
            // 否则 ScriptProvider 缓存与临时目录只涨不落
            ScriptGlueHandlers.release(jobKey);
        }
        return result;
    }

    /**
     * 分页查询任务执行日志 (按触发时刻倒序)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public ReturnResult<PageResult<JobLogVO>> pageLogs(JobLogQuery query) {
        PageResult<JobLogRecord> source = jobLogRepository.page(query);
        List<JobLogVO> rows = new ArrayList<>();
        if (source.getData() != null) {
            for (JobLogRecord record : source.getData()) {
                rows.add(JobLogVO.of(record));
            }
        }
        return ReturnResult.ok(PageResult.<JobLogVO>builder()
                .pageNo(source.getPageNo())
                .pageSize(source.getPageSize())
                .totalPages(source.getTotalPages())
                .total(source.getTotal())
                .data(rows)
                .build());
    }

    /**
     * 读取单次执行的日志文件正文。
     *
     * <p>入参是日志行记录的相对路径（{@code sys_job_log_file_path}），可能为空 —— 那说明本节点的
     * {@code plugin.job.log.enabled} 没开、这次执行根本没落文件。这种情况明确回「文件不存在」，
     * 不能回空白：空白会被读成「这次执行没打任何日志」，是两回事。</p>
     *
     * <p>路径来自前端，读取前必须规范化并确认它落在 {@code plugin.job.log.base-path} 之内，
     * 否则一个 {@code ../../} 就能把任意文件读出来。</p>
     *
     * @param filePath 日志文件相对路径
     * @return 日志正文, 含文件是否存在与是否只返回了尾部
     */
    public ReturnResult<JobLogContentVO> logContent(String filePath) {
        String relative = filePath == null ? "" : filePath.trim();
        if (relative.isEmpty()) {
            return ReturnResult.ok(new JobLogContentVO("", false, false, ""));
        }
        var base = Path.of(jobProperties.getLog().getBasePath()).toAbsolutePath().normalize();
        var target = base.resolve(relative).normalize();
        if (!target.startsWith(base)) {
            log.warn("[job] 拒绝越界的日志文件读取 path={}", relative);
            return ReturnResult.error("日志文件路径越界: " + relative);
        }
        if (!Files.isRegularFile(target)) {
            return ReturnResult.ok(new JobLogContentVO(relative, false, false, ""));
        }
        try {
            long size = Files.size(target);
            boolean truncated = size > MAX_LOG_CONTENT_BYTES;
            byte[] bytes = readLogBytes(target, size, truncated);
            return ReturnResult.ok(new JobLogContentVO(relative, true, truncated,
                    new String(bytes, StandardCharsets.UTF_8)));
        } catch (IOException ex) {
            log.warn("[job] 读取日志文件失败 path={} cause={}", relative, ex.getMessage());
            return ReturnResult.error("读取日志文件失败: " + ex.getMessage());
        }
    }

    /**
     * 读取日志字节：未超上限全读，超了只读尾部。
     *
     * <p>按字节定位可能把首字符的 UTF-8 序列切开，解码后第一个字符可能是替换符。
     * 调用方据 {@code truncated} 已知道前面被截掉了，不影响判读。</p>
     *
     * @param file      日志文件
     * @param size      文件字节数
     * @param truncated 是否只读尾部
     * @return 读到的字节
     * @throws IOException 读取失败
     */
    private static byte[] readLogBytes(Path file, long size, boolean truncated) throws IOException {
        if (!truncated) {
            return Files.readAllBytes(file);
        }
        var buffer = ByteBuffer.allocate(MAX_LOG_CONTENT_BYTES);
        try (var channel = Files.newByteChannel(file)) {
            channel.position(size - MAX_LOG_CONTENT_BYTES);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // 一次 read 不保证填满，继续读到满或流结束
            }
        }
        return buffer.array();
    }

    /**
     * 清理执行日志。
     *
     * <p>负数一律拒绝: 全清是有意的操作, 不能让一次算错的入参（把「不限制」写成 -1、或配置值
     * 多减了一）把整张日志表抹掉。0 才是显式的全清。</p>
     *
     * @param retentionDays 保留天数, 0 表示清空全部
     * @return 删除条数
     */
    public ReturnResult<Integer> cleanupLogs(int retentionDays) {
        if (retentionDays < 0) {
            return ReturnResult.error("保留天数不能为负数: " + retentionDays + ", 0 表示清空全部");
        }
        try {
            return ReturnResult.ok(jobLogRepository.cleanup(retentionDays));
        } catch (Exception ex) {
            log.error("[job] 执行日志清理失败 retentionDays={}", retentionDays, ex);
            return ReturnResult.error("清理失败: " + ex.getMessage());
        }
    }

    /**
     * 列出本进程已注册的执行器。
     *
     * <p>台账里有行但这份清单里没有同名处理器时, 那一拍会被记成
     * {@code SKIPPED · handler 未注册}, 所以这张清单是「任务能不能真跑起来」的唯一事实来源。</p>
     *
     * @return 执行器清单, 按名称排序
     */
    public ReturnResult<List<JobExecutorVO>> executors() {
        List<JobExecutorVO> rows = new ArrayList<>();
        Set<String> localNames = new HashSet<>();
        for (JobHandler handler : JobHandlerFactory.getInstance().all()) {
            JobExecutorVO vo = new JobExecutorVO();
            vo.setName(handler.name());
            vo.setDesc(handler.desc());
            vo.setHandlerClass(handler.getClass().getName());
            vo.setSource(handler instanceof BeanJobHandler
                    ? JobExecutorVO.SOURCE_ANNOTATION : JobExecutorVO.SOURCE_INJECTED);
            localNames.add(vo.getName());
            rows.add(vo);
        }
        // 合并在线执行器上报的远端处理器：调度中心节点本地没有业务处理器，不代表任务跑不了
        for (String name : remoteHandlerRegistry.all()) {
            if (localNames.contains(name)) {
                continue;
            }
            JobExecutorVO vo = new JobExecutorVO();
            vo.setName(name);
            vo.setSource(JobExecutorVO.SOURCE_REMOTE);
            rows.add(vo);
        }
        rows.sort(Comparator.comparing(JobExecutorVO::getName, Comparator.nullsLast(String::compareTo)));
        return ReturnResult.ok(rows);
    }

    /**
     * 在线执行器节点清单（「远程作业」页：节点身份、长链地址与处理器）。
     *
     * @return 节点清单，按首次登记时间升序
     */
    public ReturnResult<List<JobNodeVO>> nodes() {
        List<JobNodeVO> rows = new ArrayList<>();
        for (RemoteHandlerRegistry.NodeInfo info : remoteHandlerRegistry.nodes()) {
            JobNodeVO vo = new JobNodeVO();
            vo.setClientId(info.clientId());
            vo.setNodeId(info.nodeId());
            vo.setRole(info.role());
            vo.setServerUrl(info.serverUrl());
            List<String> handlers = new ArrayList<>(info.handlers());
            handlers.sort(Comparator.naturalOrder());
            vo.setHandlers(handlers);
            vo.setHandlerCount(handlers.size());
            vo.setRegisteredAt(info.registeredAtMs());
            rows.add(vo);
        }
        return ReturnResult.ok(rows);
    }

    /**
     * 工作台概览: 本节点角色与选主态 + 任务与日志计数。
     *
     * @return 概览
     */
    public ReturnResult<JobSummaryVO> summary() {
        JobSummaryVO vo = new JobSummaryVO();
        vo.setRole(jobProperties.getRole() == null ? null : jobProperties.getRole().name());
        vo.setNodeId(jobLeaderElector.nodeId());
        vo.setLeader(jobLeaderElector.isLeader());
        String group = jobProperties.getCluster().getGroup();
        vo.setGroup(group);
        vo.setScanIntervalMs(jobProperties.getCluster().getScanIntervalMs());
        vo.setPreReadMs(jobProperties.getCluster().getPreReadMs());
        JobHandlerFactory registry = JobHandlerFactory.getInstance();
        vo.setRegisteredHandlers(registry.size());
        // 计数必须与管辖范围一致：findDue 只扫本 group 的行，这里若传 null 就是全表统计，
        // 会把别的 group 的任务算进"本节点概览"，让 totalJobs/localJobs/remoteJobs 全部失真。
        // 注意：历史脏数据里 sys_job_group 为 default 或空的行本就永远不会被 findDue 派发
        // （见 MybatisJobRepository#defaultGroup 的说明），按 group 收敛后它们不再出现在概览里，
        // 这是与实际可调度范围对齐的结果，不是丢数据。
        List<JobRuntime> rows = jobOperator.list(group);
        long remoteCount = 0L;
        // 启用中的远程行数：只有当本节点确实会下发、却没有任何在线执行器时，这些行才真正不可达
        long remoteEnabledCount = 0L;
        // "未注册"按本地 + 在线执行器上报的并集判定：分布式下调度中心本地没有业务处理器是常态
        Set<String> knownHandlers = new HashSet<>();
        Set<String> localHandlers = new HashSet<>(JobHandlerFactory.getInstance().names());
        knownHandlers.addAll(localHandlers);
        knownHandlers.addAll(remoteHandlerRegistry.all());
        for (JobRuntime row : rows) {
            vo.setTotalJobs(vo.getTotalJobs() + 1);
            if (row.isEnabled()) {
                vo.setRunningJobs(vo.getRunningJobs() + 1);
            } else {
                vo.setStoppedJobs(vo.getStoppedJobs() + 1);
            }
            boolean isRemote = JobSpec.DISPATCH_REMOTE.equalsIgnoreCase(row.getDispatchMode());
            if (isRemote) {
                remoteCount++;
                if (row.isEnabled()) {
                    remoteEnabledCount++;
                }
            } else {
                vo.setLocalJobs(vo.getLocalJobs() + 1);
            }
            // 远程行的处理器本就在执行器节点上, 本机没有同名处理器不算漏登记; 脚本任务不依赖处理器表, 同样不算
            boolean isScript = row.getGlueType() != null
                    && !JobSpec.GLUE_BEAN.equalsIgnoreCase(row.getGlueType());
            boolean handlerKnown = knownHandlers.contains(row.getJobKey());
            if (row.isEnabled() && !isRemote && !isScript && !handlerKnown) {
                vo.setUnregisteredJobs(vo.getUnregisteredJobs() + 1);
            }
        }
        vo.setRemoteJobs(remoteCount);
        // 远程派发已内聚到带 Leader 循环的角色（单机/调度中心）的同步通道
        vo.setRemoteDispatchOwnedByMe(jobProperties.isScheduler() || jobProperties.isSingle());
        SyncServer server = syncServerProvider.getIfAvailable();
        if (server != null) {
            List<String> clients = server.getConnectedClients();
            vo.setOnlineExecutors(clients == null ? List.of() : new ArrayList<>(clients));
        }
        // 无人领取的远程行：单机角色的远程行由本机 JobExecutor 就地消费，不依赖在线执行器，
        // 因此只有「Leader + 调度中心角色 + 零在线执行器」这个组合下，启用的远程行才真正发不出去。
        //
        // 代码里已有一条兜底痕迹：SchedulerSyncBinder 在 0 在线执行器时会调用
        // recordMissedDispatch，按任务 60s 节流地尝试写一条 SKIPPED 日志（理由「无在线执行器，命令未派发」）。
        // 但本计数仍不可省略, 因为那条日志不能当作权威信号：
        //   1) 写入被 try/catch 包住, 失败只打一条 warn, 写没写成功无人知晓；
        //   2) 日志行的任务名取自 commandJobName(payload), 与台账 jobKey 不保证一致,
        //      按 jobKey 查日志未必命中这条丢单记录；
        //   3) 它是 SKIPPED, 不进任何按 FAILURE 统计的告警口径；
        //   4) 60s 节流意味着丢 1 次与丢 100 次在日志里长得一样。
        // 本计数是对台账直接求和的实时量, 不依赖日志是否写成功、是否有人翻日志、是否配了告警。
        // TODO 待验证: SKIPPED 丢单记录是否真的落库, 需在隔离节点上按 executeCode=SKIPPED 复查。
        if (jobProperties.isScheduler() && vo.isLeader() && vo.getOnlineExecutors().isEmpty()) {
            vo.setUnclaimedRemoteJobs(remoteEnabledCount);
        }
        JobLogQuery failure = new JobLogQuery();
        failure.setExecuteCode("FAILURE");
        // 必须带上本节点分组: 不带就是全库失败计数, 概览会把别的 group 的失败算到自己头上
        // （集群里各 group 各自为政，这个数字此前一直串味）。
        failure.setGroup(group);
        failure.setStartTime(System.currentTimeMillis() - FAILURE_WINDOW_MS);
        vo.setRecentFailures(jobLogRepository.count(failure));
        return ReturnResult.ok(vo);
    }

    /**
     * 分页结果换壳: 内部快照转运行态出参。
     *
     * @param source 内部分页
     * @return 出参分页
     */
    private PageResult<JobRuntimeVO> toPage(PageResult<JobRuntime> source) {
        List<JobRuntimeVO> rows = new ArrayList<>();
        if (source.getData() != null) {
            for (JobRuntime runtime : source.getData()) {
                rows.add(toVo(runtime));
            }
        }
        return PageResult.<JobRuntimeVO>builder()
                .pageNo(source.getPageNo())
                .pageSize(source.getPageSize())
                .totalPages(source.getTotalPages())
                .total(source.getTotal())
                .data(rows)
                .build();
    }

    /**
     * 统一执行入口: 校验任务键、把非法调度配置等参数问题转成可读提示。
     *
     * @param action 动作名 (仅用于日志)
     * @param jobKey 任务键
     * @param call   治理调用
     * @return 结果
     */
    private ReturnResult<Boolean> execute(String action, String jobKey, BooleanSupplier call) {
        if (jobKey == null || jobKey.isBlank()) {
            return ReturnResult.illegal("jobKey 不能为空");
        }
        try {
            return call.getAsBoolean()
                    ? ReturnResult.ok(true)
                    : ReturnResult.error("任务不存在或未被更新: " + jobKey);
        } catch (IllegalArgumentException ex) {
            return ReturnResult.illegal(ex.getMessage());
        } catch (Exception ex) {
            log.error("[job] 治理接口执行失败 action={} job={}", action, jobKey, ex);
            return ReturnResult.error("操作失败: " + ex.getMessage());
        }
    }
}
