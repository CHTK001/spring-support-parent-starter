package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.config.ServerProperties;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsExecJob;
import com.chua.starter.server.support.entity.MsExecLog;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsExecJobMapper;
import com.chua.starter.server.support.mapper.MsExecLogMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ExecService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.util.SshClients;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
* 批量执行服务实现
* <p>
* 命令执行与文件分发按主机并发执行（并发度取 {@code server.support.exec-concurrency}，
* 线程池以显式 thread游泳池执行器 构造并命名），每台主机独立记录执行日志。
* 计划任务的到期判定与下次运行时间预计算基于 Spring crontrigger。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecServiceImpl implements ExecService {

    /** 默认单机命令超时（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /** 日志查询条数上限 */
    private static final int LOG_LIMIT = 200;

    /** 输出落库截断长度，防止超长输出撑爆行存储 */
    private static final int OUTPUT_TRUNCATE = 64 * 1024;

    /** 默认分发目标目录 */
    private static final String DEFAULT_DIRECTORY = "/tmp";

    /** JSON 解析器（主机标识 数组解析） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 任务 映射器 */
    private final MsExecJobMapper jobMapper;

    /** 执行日志 映射器 */
    private final MsExecLogMapper logMapper;

    /** 主机服务 */
    private final ServerHostService serverHostService;

    /** 连接缓存管理器 */
    private final ConnectionManager connectionManager;

    /** 服务属性 */
    private final ServerProperties properties;

    @Override
    public ReturnResult<List<MsExecLog>> runCommand(List<Long> hostIds, String command, Integer timeoutSeconds, Long jobId) {
        // 参数校验前置
        if (hostIds == null || hostIds.isEmpty()) {
            return ReturnResult.error("目标主机不能为空");
        }
        if (command == null || command.isBlank()) {
            return ReturnResult.error("命令内容不能为空");
        }
        List<MsServerHost> hosts = loadHosts(hostIds);
        if (hosts.isEmpty()) {
            return ReturnResult.error("有效主机为空");
        }
        int timeout = timeoutSeconds == null || timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        List<MsExecLog> logs = executeParallel(hosts, ServerConstants.EXEC_TYPE_COMMAND, jobId,
                command, timeout, null);
        return ReturnResult.ok(logs);
    }

    @Override
    public ReturnResult<List<MsExecLog>> distributeFile(List<Long> hostIds, String directory, MultipartFile file) {
        if (hostIds == null || hostIds.isEmpty()) {
            return ReturnResult.error("目标主机不能为空");
        }
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("分发文件不能为空");
        }
        String dir = directory == null || directory.isBlank() ? DEFAULT_DIRECTORY : directory;
        List<MsServerHost> hosts = loadHosts(hostIds);
        if (hosts.isEmpty()) {
            return ReturnResult.error("有效主机为空");
        }
        Path tempFile;
        try {
            tempFile = Files.createTempFile("ms-exec-dist-", ".bin");
            file.transferTo(tempFile.toFile());
        } catch (IOException e) {
            return ReturnResult.error("保存上传文件失败: " + e.getMessage());
        }
        try {
            List<MsExecLog> logs = executeParallel(hosts, ServerConstants.EXEC_TYPE_FILE, null,
                    dir + "/" + sanitize(file.getOriginalFilename()), DEFAULT_TIMEOUT_SECONDS * 10, tempFile);
            long failed = logs.stream().filter(l -> !ServerConstants.EXEC_STATUS_SUCCESS.equals(l.getMsExecLogStatus())).count();
            if (failed > 0) {
                // 部分失败：携带完整日志返回，便于前端逐主机展示
                return ReturnResult.<List<MsExecLog>>error(failed + " 台主机分发失败，详见日志").data(logs);
            }
            return ReturnResult.ok(logs, "分发成功");
        } finally {
            /**
            * cleanupquietly。
            * @param tempFile temp文件
            */
            cleanupQuietly(tempFile);
        }
    }

    @Override
    public ReturnResult<MsExecJob> createJob(MsExecJob job) {
        var validate = validateJob(job);
        if (validate != null) {
            return validate;
        }
        if (job.getMsExecJobTimeout() == null || job.getMsExecJobTimeout() <= 0) {
            job.setMsExecJobTimeout(DEFAULT_TIMEOUT_SECONDS);
        }
        if (job.getMsExecJobEnabled() == null) {
            job.setMsExecJobEnabled(1);
        }
        // 预计算首次运行时间，便于调度器快速判定
        job.setMsExecJobNextRunTime(nextFireTime(job.getMsExecJobCron(), LocalDateTime.now()));
        jobMapper.insert(job);
        return ReturnResult.ok(jobMapper.selectById(job.getId()));
    }

    @Override
    public ReturnResult<MsExecJob> updateJob(Long id, MsExecJob job) {
        MsExecJob exists = jobMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("任务不存在: " + id);
        }
        if (!isBlank(job.getMsExecJobCron())) {
            exists.setMsExecJobCron(job.getMsExecJobCron());
            exists.setMsExecJobNextRunTime(nextFireTime(job.getMsExecJobCron(), LocalDateTime.now()));
        } else if (exists.getMsExecJobNextRunTime() != null && exists.getMsExecJobLastRunTime() != null) {
            exists.setMsExecJobNextRunTime(nextFireTime(exists.getMsExecJobCron(), exists.getMsExecJobLastRunTime()));
        }
        if (!isBlank(job.getMsExecJobName())) {
            exists.setMsExecJobName(job.getMsExecJobName());
        }
        if (!isBlank(job.getMsExecJobCommand())) {
            exists.setMsExecJobCommand(job.getMsExecJobCommand());
        }
        if (job.getMsExecJobHostIds() != null) {
            exists.setMsExecJobHostIds(job.getMsExecJobHostIds());
        }
        if (job.getMsExecJobTimeout() != null && job.getMsExecJobTimeout() > 0) {
            exists.setMsExecJobTimeout(job.getMsExecJobTimeout());
        }
        if (job.getMsExecJobEnabled() != null) {
            exists.setMsExecJobEnabled(job.getMsExecJobEnabled());
        }
        if (job.getMsExecJobRemark() != null) {
            exists.setMsExecJobRemark(job.getMsExecJobRemark());
        }
        jobMapper.updateById(exists);
        return ReturnResult.ok(jobMapper.selectById(id));
    }

    @Override
    public ReturnResult<Void> deleteJob(Long id) {
        jobMapper.deleteById(id);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<List<MsExecJob>> listJobs() {
        return ReturnResult.list(jobMapper.selectList(
                new LambdaQueryWrapper<MsExecJob>().orderByDesc(MsExecJob::getId)));
    }

    @Override
    public ReturnResult<List<MsExecLog>> runJobNow(Long id) {
        MsExecJob job = jobMapper.selectById(id);
        if (job == null) {
            return ReturnResult.error("任务不存在: " + id);
        }
        return runCommand(parseHostIds(job.getMsExecJobHostIds()),
                job.getMsExecJobCommand(), job.getMsExecJobTimeout(), job.getId());
    }

    @Override
    public ReturnResult<List<MsExecLog>> listLogs(String type, Long hostId) {
        var wrapper = new LambdaQueryWrapper<MsExecLog>();
        if (!isBlank(type)) {
            wrapper.eq(MsExecLog::getMsExecLogType, type);
        }
        if (hostId != null) {
            wrapper.eq(MsExecLog::getMsExecLogHostId, hostId);
        }
        wrapper.orderByDesc(MsExecLog::getId).last("LIMIT " + LOG_LIMIT);
        return ReturnResult.list(logMapper.selectList(wrapper));
    }

    // ==================== 调度支撑 ====================

    /**
    * 扫描到期任务并触发执行（由 执行作业调度器 定时调用）
    *
    * @return 本次触发的任务数
    */
    @Override
    public int tickDueJobs() {
        List<MsExecJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<MsExecJob>()
                .eq(MsExecJob::getMsExecJobEnabled, 1));
        int fired = 0;
        for (MsExecJob job : jobs) {
            LocalDateTime next = job.getMsExecJobNextRunTime();
            boolean due = next == null || !next.isAfter(LocalDateTime.now());
            if (!due) {
                continue;
            }
            fired++;
            /**
            * trigger作业。
            * @param job 作业
            */
            triggerJob(job);
        }
        return fired;
    }

    /**
    * 触发单个计划任务：执行命令并回写运行时间
    *
    * @param job 任务
    */
    private void triggerJob(MsExecJob job) {
        try {
            List<Long> hostIds = parseHostIds(job.getMsExecJobHostIds());
 // 透传 作业标识，日志在 执行单个 中统一落库（不再重复 插入）
            /**
            * 运行命令。
            * @param hostIds 主机标识
            * @param job.getMsExecJobCommand( 作业.getms执行作业命令(
            */
            runCommand(hostIds, job.getMsExecJobCommand(), job.getMsExecJobTimeout(), job.getId());
        } catch (Exception e) {
            log.warn("[exec] 计划任务执行异常 jobId={}: {}", job.getId(), e.getMessage());
        } finally {
            LocalDateTime now = LocalDateTime.now();
            job.setMsExecJobLastRunTime(now);
            job.setMsExecJobNextRunTime(nextFireTime(job.getMsExecJobCron(), now));
            jobMapper.updateById(job);
        }
    }

    // ==================== 并发执行内核 ====================

    /**
    * 按主机并发执行：命令或文件分发
    *
    * @param hosts          主机列表
    * @param type           日志类型 命令 / 文件
    * @param jobId          关联任务 标识，可为空
    * @param commandOrPath  命令或"目录/文件名"
    * @param timeoutSeconds 超时（秒）
    * @param localFile      分发的本地文件，命令场景为 空
    * @return 各主机执行日志
    */
    private List<MsExecLog> executeParallel(List<MsServerHost> hosts, String type, Long jobId,
                                            String commandOrPath, int timeoutSeconds, Path localFile) {
        ExecutorService pool = newPool(Math.min(properties.getExecConcurrency(), Math.max(1, hosts.size())));
        try {
            List<Future<MsExecLog>> futures = new ArrayList<>(hosts.size());
            for (MsServerHost host : hosts) {
                futures.add(pool.submit(() -> executeSingle(host, type, jobId, commandOrPath,
                        timeoutSeconds, localFile)));
            }
            List<MsExecLog> logs = new ArrayList<>(futures.size());
            // 总等待留出余量，避免个别主机拖死整体请求
            long overallWaitMs = (timeoutSeconds + 30L) * 1000L;
            for (Future<MsExecLog> future : futures) {
                try {
                    logs.add(future.get(overallWaitMs, TimeUnit.MILLISECONDS));
                } catch (Exception e) {
                    logs.add(buildFailureLog(type, null, commandOrPath, "执行等待超时或中断: " + e.getMessage()));
                }
            }
            return logs;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
    * 单主机执行：命令走 SSH 执行，文件走 SFTP 上传
    *
    * @param host           主机
    * @param type           日志类型
    * @param jobId          任务 标识
    * @param commandOrPath  命令或远端目标路径
    * @param timeoutSeconds 超时（秒）
    * @param localFile      本地文件（文件分发场景）
    * @return 执行日志
    */
    private MsExecLog executeSingle(MsServerHost host, String type, Long jobId,
                                    String commandOrPath, int timeoutSeconds, Path localFile) {
        long startMs = System.currentTimeMillis();
        MsExecLog item = new MsExecLog();
        item.setMsExecLogType(type);
        item.setMsExecLogHostId(host.getId());
        item.setMsExecLogHostName(host.getServerHostName());
        item.setMsExecLogJobId(jobId);
        item.setMsExecLogCommand(commandOrPath);
        item.setMsExecLogStartTime(LocalDateTime.now());
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(host.getId(), null);
        if (!resolved.isOk()) {
            return fail(item, resolved.getMsg(), startMs);
        }
        try {
            if (localFile != null) {
                /**
                * uploadviasftp。
                * @param host 主机
                * @param resolved.getData( resolved.获取数据(
                */
                uploadViaSftp(host, resolved.getData(), commandOrPath, localFile);
            } else {
                /**
                * 执行viassh。
                * @param host 主机
                * @param resolved.getData( resolved.获取数据(
                */
                execViaSsh(host, resolved.getData(), commandOrPath, timeoutSeconds, item);
            }
            item.setMsExecLogStatus(ServerConstants.EXEC_STATUS_SUCCESS);
            item.setMsExecLogExitCode(item.getMsExecLogExitCode() == null ? 0 : item.getMsExecLogExitCode());
        } catch (Exception e) {
            return fail(item, e.getMessage(), startMs);
        }
        /**
        * fillcost。
        * @param item item
        * @param startMs 启动ms
        */
        fillCost(item, startMs);
        /**
        * persistquietly。
        * @param item item
        */
        persistQuietly(item);
        return item;
    }

    /**
    * SSH 执行命令并填充结果
    *
    * @param host       主机
    * @param credential 凭据
    * @param command    命令
    * @param timeout    超时（秒）
    * @param item       待填充日志
    * @throws Exception 连接或执行失败时抛出
    */
    private void execViaSsh(MsServerHost host, SshCredential credential, String command,
                            int timeout, MsExecLog item) throws Exception {
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            var result = handle.client().exec().command(command).execute();
            item.setMsExecLogOutput(truncate(result.stdout()));
            item.setMsExecLogExitCode(result.exitCode());
            if (result.exitCode() != 0 && isBlank(result.stderr())) {
                /**
                * illegal状态异常。
                * @param result.exitCode( 结果.exit编码(
                * @return illegal状态异常的结果
                */
                throw new IllegalStateException("退出码非 0: " + result.exitCode());
            }
            if (result.exitCode() != 0) {
                /**
                * illegal状态异常。
                * @param result.stderr( 结果.stderr(
                * @return illegal状态异常的结果
                */
                throw new IllegalStateException(result.stderr());
            }
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

    /**
    * SFTP 上传文件
    *
    * @param host       主机
    * @param credential 凭据
    * @param remotePath 远端完整路径
    * @param localFile  本地文件
    * @throws Exception 上传失败时抛出
    */
    private void uploadViaSftp(MsServerHost host, SshCredential credential,
                               String remotePath, Path localFile) throws Exception {
        var client = buildConnectedSftp(host, credential);
        try {
            client.upload().local(localFile.toString()).remote(remotePath).exec();
        } finally {
            client.disconnect();
        }
    }

    /**
    * 构建已连接的 SFTP 客户端
    * <p>
    * 私钥经临时文件装配，连接后立即删除临时文件。
    * </p>
    *
    * @param host       主机
    * @param credential 凭据
    * @return 已连接客户端
    * @throws IOException 临时私钥写入失败时抛出
    */
    private com.chua.ssh.support.client.SftpClient buildConnectedSftp(MsServerHost host,
                                                                      SshCredential credential) throws IOException {
        var builder = com.chua.ssh.support.client.SftpClient.builder()
                .host(host.getServerHostAddress())
                .port(host.getServerHostPort() == null ? 22 : host.getServerHostPort())
                .username(credential.username())
                .connectTimeout(8);
        Path tempKey = null;
        try {
            if (credential.hasKey()) {
                tempKey = Files.createTempFile("ms-server-key-", ".pem");
                Files.writeString(tempKey, credential.privateKey());
                builder.privateKey(tempKey.toString());
                if (!isBlank(credential.password())) {
                    builder.password(credential.password());
                }
            } else {
                builder.password(credential.password() == null ? "" : credential.password());
            }
            var client = builder.build();
            client.connect();
            return client;
        } finally {
            /**
            * cleanupquietly。
            * @param tempKey temp键
            */
            cleanupQuietly(tempKey);
        }
    }

    /**
    * 加载并校验目标主机列表
    *
    * @param hostIds 主机 标识 集合
    * @return 存在的主机列表
    */
    private List<MsServerHost> loadHosts(List<Long> hostIds) {
        List<MsServerHost> hosts = new ArrayList<>(hostIds.size());
        for (Long id : hostIds) {
            var result = serverHostService.get(id);
            if (result.isOk()) {
                hosts.add(result.getData());
            }
        }
        return hosts;
    }

    /**
    * 解析 主机标识 JSON 数组
    *
    * @param json JSON 字符串
    * @return ID 列表，解析失败返回空列表
    */
    private List<Long> parseHostIds(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() { });
        } catch (IOException e) {
            log.warn("[exec] hostIds 解析失败: {}", json);
            return List.of();
        }
    }

    /**
    * 校验计划任务参数
    *
    * @param job 任务
    * @return 校验失败的错误结果，合法返回 空
    */
    private ReturnResult<MsExecJob> validateJob(MsExecJob job) {
        if (job == null || isBlank(job.getMsExecJobName()) || isBlank(job.getMsExecJobCron())
                || isBlank(job.getMsExecJobCommand())) {
            return ReturnResult.error("任务名称、cron 表达式与命令均不能为空");
        }
        if (parseHostIds(job.getMsExecJobHostIds()).isEmpty()) {
            return ReturnResult.error("至少选择一台目标主机");
        }
 // 借助 crontrigger 构造校验表达式合法性（非法表达式抛出异常）
        try {
            /**
            * 下一个fire时间。
            * @param job.getMsExecJobCron( 作业.getms执行作业cron(
            */
            nextFireTime(job.getMsExecJobCron(), LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            return ReturnResult.error("cron 表达式非法: " + job.getMsExecJobCron());
        }
        return null;
    }

    /**
    * 计算下一次运行时间
    *
    * @param cron cron 表达式
    * @param base 时间基准
    * @return 下次运行时间，计算失败返回 空
    */
    private LocalDateTime nextFireTime(String cron, LocalDateTime base) {
        Instant baseInstant = base.atZone(ZoneId.systemDefault()).toInstant();
        var context = new SimpleTriggerContext(baseInstant.minusSeconds(60), baseInstant.minusSeconds(60), baseInstant);
        Instant next = new CronTrigger(cron).nextExecution(context);
        return next == null ? null : LocalDateTime.ofInstant(next, ZoneId.systemDefault());
    }

    /**
    * 创建带命名线程的固定线程池
    * <p>
    * P3C 要求禁用 执行器 工厂方法：此处以显式 thread游泳池执行器
    * 构造，队列有界，拒绝策略为调用方执行。
    * </p>
    *
    * @param size 线程数
    * @return 线程池
    */
    private ExecutorService newPool(int size) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "server-exec-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new java.util.concurrent.ThreadPoolExecutor(size, size, 30L, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(200), factory,
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
    * 构造失败日志
    *
    * @param type   类型
    * @param host   主机，可为空
    * @param target 命令/路径
    * @param reason 失败原因
    * @return 日志对象
    */
    private MsExecLog buildFailureLog(String type, MsServerHost host, String target, String reason) {
        MsExecLog item = new MsExecLog();
        item.setMsExecLogType(type);
        item.setMsExecLogHostId(host == null ? null : host.getId());
        item.setMsExecLogHostName(host == null ? null : host.getServerHostName());
        item.setMsExecLogCommand(target);
        item.setMsExecLogStatus(ServerConstants.EXEC_STATUS_FAIL);
        item.setMsExecLogError(reason);
        /**
        * fillcost。
        * @param item item
        * @param System.currentTimeMillis( 系统.当前时间millis(
        */
        fillCost(item, System.currentTimeMillis());
        /**
        * persistquietly。
        * @param item item
        */
        persistQuietly(item);
        return item;
    }

    /**
    * 标记日志失败并落库
    *
    * @param item    日志
    * @param reason  原因
    * @param startMs 开始毫秒
    * @return 同一日志对象
    */
    private MsExecLog fail(MsExecLog item, String reason, long startMs) {
        item.setMsExecLogStatus(ServerConstants.EXEC_STATUS_FAIL);
        item.setMsExecLogError(reason);
        /**
        * fillcost。
        * @param item item
        * @param startMs 启动ms
        */
        fillCost(item, startMs);
        /**
        * persistquietly。
        * @param item item
        */
        persistQuietly(item);
        return item;
    }

    /**
    * 填充结束时间与耗时
    *
    * @param item    日志
    * @param startMs 开始毫秒
    */
    private void fillCost(MsExecLog item, long startMs) {
        long endMs = System.currentTimeMillis();
        item.setMsExecLogEndTime(LocalDateTime.now());
        item.setMsExecLogCostMs(endMs - startMs);
    }

    /**
    * 静默落库（审计不阻断主流程）
    *
    * @param item 日志
    */
    private void persistQuietly(MsExecLog item) {
        try {
            logMapper.insert(item);
        } catch (Exception e) {
            log.warn("[exec] 执行日志落库失败: {}", e.getMessage());
        }
    }

    /**
    * 截断超长输出
    *
    * @param output 原始输出
    * @return 截断后输出
    */
    private String truncate(String output) {
        if (output == null || output.length() <= OUTPUT_TRUNCATE) {
            return output;
        }
        return output.substring(0, OUTPUT_TRUNCATE) + "\n...[已截断]";
    }

    /**
    * 清洗文件名
    *
    * @param original 原始名
    * @return 安全名
    */
    private String sanitize(String original) {
        String name = original == null ? "file" : original.replace("\\", "_").replace("/", "_");
        return name.isEmpty() ? "file" : name;
    }

    /**
    * 静默清理临时文件
    *
    * @param path 文件路径
    */
    private void cleanupQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败交由操作系统兜底
        }
    }

    /**
    * 空串判断
    *
    * @param value 待判断字符串
    * @return 是否为空
    */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
