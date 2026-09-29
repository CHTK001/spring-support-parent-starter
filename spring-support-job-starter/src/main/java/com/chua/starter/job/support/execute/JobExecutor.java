package com.chua.starter.job.support.execute;

import com.chua.starter.job.support.dispatch.JobSpec;
import com.chua.starter.job.support.glue.GlueTypes;
import com.chua.starter.job.support.glue.GlueTypes;
import com.chua.starter.job.support.handler.JobDispatchContext;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.JobLogEvent;
import com.chua.starter.job.support.log.JobLogHelper;
import com.chua.starter.job.support.sync.JobSyncBus;
import com.chua.starter.job.support.sync.JobSyncTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 执行器 / 单个 角色任务执行入口。
 *
 * <p>消费 {@link JobSyncBus} 上的 {@link JobSyncTopics#TASK_COMMAND} 事件,
 * 提交到内置执行器, 处理器 成功执行后构造
 * {@link JobLogEvent} 通过 bus 上的 {@link JobSyncTopics#TASK_LOG} 投递。
 * 执行器可为平台 {@link ThreadPoolExecutor} 或虚拟线程执行器 (见 {@link #defaultExecutor()})。</p>
 *
 * <p>SCHEDULER 角色不会注入本类; SINGLE 角色既消费也执行。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class JobExecutor {

    /**
     * 默认线程名前缀
     */
    public static final String DEFAULT_THREAD_NAME_PREFIX = "job-exec-";

    /**
     * 默认核心线程数
     */
    public static final int DEFAULT_CORE_SIZE = 8;

    /**
     * 默认最大线程数
     */
    public static final int DEFAULT_MAX_SIZE = 32;

    /**
     * 默认队列容量
     */
    public static final int DEFAULT_QUEUE_CAPACITY = 1000;

    /**
     * 回调/告警等附属动作的超时（毫秒）。
     *
     * <p>业务处理器有台账 {@code executeTimeout} 兜底，附属动作（重试回调/异常回调/告警通道）
     * 同样需要上限：挂死的回调不能把任务线程钉住、让这一轮永远不出终态日志。
     * 可用 {@code -Djob.callback.timeout.ms=...} 调整（测试用）。</p>
     */
    private static final long CALLBACK_TIMEOUT_MS = Long.getLong("job.callback.timeout.ms", 30_000L);

    /**
     * 任务处理器注册表
     */
    private final JobHandlerFactory factory;

    /**
     * 事件总线
     */
    private final JobSyncBus bus;

    /**
     * 当前节点标识
     */
    private final String nodeId;

    /**
     * 执行器 (可为平台线程池或虚拟线程执行器)
     */
    private final ExecutorService executor;

    /**
     * 失败告警通道（可空：未接入时只记日志，不影响任务终态）
     */
    private final com.chua.starter.job.support.notify.JobAlarmNotifier alarmNotifier;

    /**
     * 已处理任务计数
     */
    private final AtomicLong processed = new AtomicLong(0L);

    /**
     * 已丢弃任务计数
     */
    private final AtomicLong dropped = new AtomicLong(0L);

    /**
     * 已执行 初始化 生命周期钩子的 处理器 名称集合 (惰性, 每 处理器 仅一次)
     */
    private final java.util.Set<String> initializedHandlers =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 正在执行中的任务名集合 (并发互斥依据)。
     *
     * <p>必须<b>在提交线程任务之前</b>就登记, 否则连续两拍命令会双双通过检查;
     * 任务真正结束时在 runnable 的 finally 里摘除。</p>
     */
    private final java.util.Set<String> runningJobs =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 因并发策略被跳过的次数
     */
    private final AtomicLong concurrentSkipped = new AtomicLong(0L);

    /**
     * 负载未携带并发策略时的兜底值 (保持历史行为 ALLOW)
     */
    private final String defaultConcurrentPolicy;

    /**
     * 宿主容器，供代码片段取 Spring Bean (可为 null, 此时片段只能用静态逻辑)
     */
    private final ApplicationContext applicationContext;

    /**
     * CMD 监听器引用 (持有, 用于注销)
     */
    private final java.util.function.BiConsumer<String, Object> cmdListener = (topic, payload) -> onCommand(topic, payload);

    /**
     * 构造执行器, 使用默认线程池 (虚拟线程执行器)。
     *
     * @param factory 处理器注册表
     * @param bus     进程内总线
     * @param nodeId  当前节点标识
     */
    public JobExecutor(JobHandlerFactory factory, JobSyncBus bus, String nodeId) {
        this(factory, bus, nodeId, defaultExecutor(), null);
    }

    /**
     * 构造执行器 (由 {@link com.chua.starter.job.support.JobAutoConfiguration} 装配)。
     *
     * @param factory  处理器注册表
     * @param bus      进程内总线
     * @param nodeId   当前节点标识
     * @param executor 自定义执行器
     */
    public JobExecutor(JobHandlerFactory factory, JobSyncBus bus, String nodeId,
                       ExecutorService executor) {
        this(factory, bus, nodeId, executor, null);
    }

    /**
     * 构造执行器（带失败告警通道）。
     *
     * @param factory       处理器注册表
     * @param bus           进程内总线
     * @param nodeId        当前节点标识
     * @param executor      自定义执行器
     * @param alarmNotifier 告警通道，可为 null（未接入时只记日志）
     */
    public JobExecutor(JobHandlerFactory factory, JobSyncBus bus, String nodeId,
                       ExecutorService executor,
                       com.chua.starter.job.support.notify.JobAlarmNotifier alarmNotifier) {
        this(factory, bus, nodeId, executor, alarmNotifier, JobSpec.CONCURRENT_ALLOW);
    }

    /**
     * 构造执行器（带失败告警通道与兜底并发策略）。
     *
     * @param factory                 处理器注册表
     * @param bus                     进程内总线
     * @param nodeId                  当前节点标识
     * @param executor                自定义执行器
     * @param alarmNotifier           失败告警通道，可为 null（未接入时只记日志）
     * @param defaultConcurrentPolicy 负载未携带 {@code cc} 时的兜底并发策略
     */
    public JobExecutor(JobHandlerFactory factory, JobSyncBus bus, String nodeId,
                       ExecutorService executor,
                       com.chua.starter.job.support.notify.JobAlarmNotifier alarmNotifier,
                       String defaultConcurrentPolicy) {
        this(factory, bus, nodeId, executor, alarmNotifier, defaultConcurrentPolicy, null);
    }

    /**
     * 构造执行器（全部可选项齐备，由自动配置装配）。
     *
     * <p>{@code applicationContext} 供代码片段取 Spring bean：Groovy 脚本侧绑成
     * {@code objectContext} 变量，Java 片段侧还会触发一次 {@code @Autowired} 属性装配。
     * 传 null 时片段仍能跑，但 {@code getBean} 会得到可读的「未装配容器」异常。</p>
     *
     * @param factory                 处理器注册表
     * @param bus                     进程内总线
     * @param nodeId                  当前节点标识
     * @param executor                自定义执行器
     * @param alarmNotifier           失败告警通道，可为 null（未接入时只记日志）
     * @param defaultConcurrentPolicy 负载未携带 {@code cc} 时的兜底并发策略
     * @param applicationContext      宿主容器，供代码片段取 Spring bean，可为 null
     */
    public JobExecutor(JobHandlerFactory factory, JobSyncBus bus, String nodeId,
                       ExecutorService executor,
                       com.chua.starter.job.support.notify.JobAlarmNotifier alarmNotifier,
                       String defaultConcurrentPolicy,
                       ApplicationContext applicationContext) {
        this.factory = factory;
        this.bus = bus;
        this.nodeId = nodeId;
        this.executor = executor;
        this.alarmNotifier = alarmNotifier;
        this.defaultConcurrentPolicy = defaultConcurrentPolicy == null || defaultConcurrentPolicy.isBlank()
                ? JobSpec.CONCURRENT_ALLOW
                : defaultConcurrentPolicy.trim();
        this.applicationContext = applicationContext;
        bus.subscribe(JobSyncTopics.TASK_COMMAND, cmdListener);
    }

    /**
     * 接收到 CMD 事件的回调。
     *
     * @param topic   topic 名 (固定为 任务_命令)
     * @param payload 业务负载 (任务名)
     * @param source 源
     */
    private void onCommand(String source, Object payload) {
        if (!(payload instanceof String raw)) {
            return;
        }
        JobSyncTopics.Command command = JobSyncTopics.parse(raw);
        String jobName = command.jobName();
        log.info("[job] 收到 cmd source={} job={} manual={} retry={} timeout={}s glue={}",
                source, jobName, command.manual(), command.failRetry(), command.executeTimeoutSec(),
                command.glueType());
        long now = System.currentTimeMillis();
        JobHandler handler;
        if (isGlueCommand(command)) {
            // 代码片段任务：命令带正文直接用，只带版本号时从本地缓存解析
            // （正文由 job-glue 主题单独同步）；引擎缺失/编译失败/正文未同步记 FAILURE 而不是静默跳过
            try {
                handler = resolveGlueHandler(command, jobName);
                log.info("[job] 片段任务处理器就绪 job={} glueType={} glueAt={}",
                        jobName, command.glueType(), command.glueUpdatetimeMs());
            } catch (Throwable ex) {
                log.error("[job] 片段任务不可执行 job={} glueType={} cause={}",
                        jobName, command.glueType(), ex.getMessage());
                bus.publish(JobSyncTopics.TASK_LOG, nodeId,
                        JobLogEvent.failure(jobName, 0L, ex, now, nodeId,
                                "Exception: " + ex.getMessage() + "\n",
                                command.manual() ? JobLogEvent.TRIGGER_MANUAL : null));
                processed.incrementAndGet();
                return;
            }
        } else {
            handler = factory.get(jobName);
        }
        if (handler == null) {
            log.warn("[job] handler 未注册 job={}", jobName);
            bus.publish(JobSyncTopics.TASK_LOG, nodeId,
                    JobLogEvent.skipped(jobName, "handler 未注册", nodeId));
            dropped.incrementAndGet();
            return;
        }
        // 并发互斥: 上一轮未结束时按台账策略决定跳过还是放行。
        // 登记必须早于提交, 否则连续两拍会双双通过检查后又各提交一份。
        String policy = resolveConcurrentPolicy(command);
        if (!JobSpec.CONCURRENT_ALLOW.equals(policy)) {
            if (!runningJobs.add(jobName)) {
                concurrentSkipped.incrementAndGet();
                log.warn("[job] 上一轮未结束, 按并发策略跳过本拍 job={} policy={}", jobName, policy);
                bus.publish(JobSyncTopics.TASK_LOG, nodeId,
                        JobLogEvent.skipped(jobName,
                                "上一轮尚未结束, 并发策略 " + policy + " 跳过本拍", nodeId));
                return;
            }
        }
        try {
            executor.execute(() -> {
                try {
                    invoke(jobName, handler, command);
                } finally {
                    runningJobs.remove(jobName);
                }
            });
        } catch (RejectedExecutionException ex) {
            // 提交被拒时本轮不会执行, 必须摘掉刚登记的占用, 否则任务被永久判为"运行中"
            runningJobs.remove(jobName);
            dropped.incrementAndGet();
            log.warn("[job] 线程池拒绝 job={} cause={}", jobName, ex.getMessage());
        }
    }

    /**
     * 解析本拍的并发策略: 负载带 {@code cc} 时以它为准, 否则用执行器兜底值。
     *
     * <p>兜底值取 ALLOW 而不是 SKIP: 负载不带该字段说明对端是未升级的旧调度中心,
     * 此时保持历史行为 (允许并发) 才不会让分布式下的任务被无端跳过。</p>
     *
     * @param command 派发命令
     * @return SKIP / ALLOW (非空)
     */
    private String resolveConcurrentPolicy(JobSyncTopics.Command command) {
        String carried = command.concurrentPolicy();
        if (carried == null || carried.isBlank()) {
            return defaultConcurrentPolicy;
        }
        return JobSpec.normalizeConcurrentPolicy(carried);
    }

    /**
     * 解析代码片段任务的处理器。
     *
     * <p>命令带正文时直接合成/预编译，只带版本号时从本地缓存取（正文已由
     * {@code job-glue} 主题单独同步）。脚本与片段、Java 与 Groovy 都是**同一条 SPI 链路**：
     * 片段类型先合成完整脚本，再统一交给 {@code ScriptProvider} 引擎执行，
     * 不再有旁路编译器。</p>
     *
     * @param command 派发命令
     * @param jobName 任务名
     * @return 可执行处理器
     * @throws IllegalStateException 引擎缺失、编译失败或正文未同步
     */
    private JobHandler resolveGlueHandler(JobSyncTopics.Command command, String jobName) {
        boolean inlineSource = command.glueSource() != null && !command.glueSource().isBlank();
        return inlineSource
                ? ScriptGlueHandlers.resolve(jobName, command.glueType(), command.glueSource(),
                        command.glueUpdatetimeMs(), command.executeParam(), applicationContext)
                : ScriptGlueHandlers.resolveCached(jobName, command.glueType(),
                        command.glueUpdatetimeMs(), command.executeParam(), applicationContext);
    }

    /**
     * 判断命令是否代码片段任务（执行类型非 BEAN；正文可缺省，由本地缓存解析）。
     *
     * @param command 派发命令
     * @return 片段任务（脚本或 Java 代码片段）返回 true
     */
    private static boolean isGlueCommand(JobSyncTopics.Command command) {
        return GlueTypes.isGlue(command.glueType());
    }

    /**
     * 实际调用处理器：按命令策略执行失败重试与执行超时，抛异常也仅记日志。
     *
     * <p>重试次数 = 1 + failRetry（failRetry=2 表示首跑 + 2 次重试，共 3 次尝试）；
     * 超时按单次尝试计时，超时后中断该次尝试并按失败处理。一次派发只上行一条终态日志，
     * 尝试过程写进日志正文。</p>
     *
     * @param jobName 任务名
     * @param handler 处理器
     * @param command 派发命令（含 manual / failRetry / executeTimeoutSec）
     */
    private void invoke(String jobName, JobHandler handler, JobSyncTopics.Command command) {
        boolean manual = command.manual();
        int failRetry = command.failRetry() == null || command.failRetry() < 0 ? 0 : command.failRetry();
        int maxAttempts = 1 + failRetry;
        int retryIntervalSec = command.retryIntervalSec() == null || command.retryIntervalSec() < 0
                ? 0 : command.retryIntervalSec();
        int timeoutSec = command.executeTimeoutSec() == null || command.executeTimeoutSec() < 0
                ? 0 : command.executeTimeoutSec();
        String triggerType = manual ? JobLogEvent.TRIGGER_MANUAL : null;
        long start = System.currentTimeMillis();
        StringBuilder collected = new StringBuilder();
        Attempt last = null;
        int attemptUsed = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            attemptUsed = attempt;
            Attempt current = runAttempt(jobName, handler, manual, timeoutSec, command.executeParam());
            last = current;
            if (current.logContent() != null) {
                collected.append(current.logContent());
            }
            if (current.ok()) {
                break;
            }
            if (attempt < maxAttempts) {
                String reason = current.error() == null ? "unknown" : String.valueOf(current.error().getMessage());
                collected.append("[retry] 第 ").append(attempt).append(" 次失败: ")
                        .append(reason).append(", 准备第 ").append(attempt + 1).append(" 次重试\n");
                log.warn("[job] 执行失败准备重试 job={} attempt={}/{} interval={}s cause={}",
                        jobName, attempt, maxAttempts, retryIntervalSec, reason);
                invokeCallback(command.retryCallbackBean(), "retry", jobName, collected, timeoutSec);
                if (!sleepBeforeRetry(jobName, retryIntervalSec)) {
                    collected.append("[retry] 重试等待被中断, 停止后续重试\n");
                    break;
                }
            }
        }
        long cost = System.currentTimeMillis() - start;
        processed.incrementAndGet();
        if (last != null && last.ok()) {
            log.info("[job] invoke ok job={} cost={}ms attempt={}/{}", jobName, cost, attemptUsed, maxAttempts);
            bus.publish(JobSyncTopics.TASK_LOG, nodeId,
                    JobLogEvent.success(jobName, cost, start, nodeId, joinLog(collected), triggerType));
        } else {
            Throwable error = last == null || last.error() == null
                    ? new IllegalStateException("任务未产生执行结果") : last.error();
            // 异常回调与失败告警：只影响日志正文，不改变任务终态；均带独立超时，挂死不会拖住任务线程
            invokeCallback(command.exceptionCallbackBean(), "exception", jobName, collected, timeoutSec);
            notifyAlarm(jobName, command.alarmType(), command.alarmEmail(), cost, error, collected, timeoutSec);
            String logContent = collected.toString() + "Exception: " + error.getMessage() + "\n";
            log.error("[job] 执行失败 job={} cause={} attempt={}/{}", jobName, error.getMessage(), attemptUsed, maxAttempts, error);
            bus.publish(JobSyncTopics.TASK_LOG, nodeId,
                    JobLogEvent.failure(jobName, cost, error, start, nodeId, logContent, triggerType));
        }
    }

    /**
     * 调用回调处理器（未注册/执行失败只记 WARN 与日志正文，不改变任务终态）。
     *
     * @param callbackName 回调处理器名
     * @param phase        阶段（retry / exception）
     * @param jobName      任务名
     * @param collected    日志正文缓冲
     * @param timeoutSec   任务执行超时秒数（0 为不限），用于收敛附属动作上限
     */
    private void invokeCallback(String callbackName, String phase, String jobName, StringBuilder collected,
                                int timeoutSec) {
        if (callbackName == null || callbackName.isBlank()) {
            return;
        }
        String name = callbackName.trim();
        JobHandler callback = factory.get(name);
        if (callback == null) {
            log.warn("[job] {} 回调未注册 job={} callback={}", phase, jobName, name);
            collected.append('[').append(phase).append("] 回调未注册: ").append(name).append('\n');
            return;
        }
        try {
            runBounded(phase, callback::execute, collected, sideEffectLimitMs(timeoutSec));
            collected.append('[').append(phase).append("] 回调已执行: ").append(name).append('\n');
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            collected.append('[').append(phase).append("] 回调被中断: ").append(name).append('\n');
        } catch (Throwable ex) {
            log.warn("[job] {} 回调执行失败 job={} callback={} cause={}", phase, jobName, name, ex.getMessage());
            collected.append('[').append(phase).append("] 回调失败: ").append(name)
                    .append(" - ").append(ex.getMessage()).append('\n');
        }
    }

    /**
     * 附属动作超时上限：任务配了执行超时则取更小者，否则用默认回调上限。
     *
     * @param timeoutSec 任务执行超时秒数（0 为不限）
     * @return 上限毫秒
     */
    private static long sideEffectLimitMs(int timeoutSec) {
        return timeoutSec > 0 ? Math.min(CALLBACK_TIMEOUT_MS, timeoutSec * 1000L) : CALLBACK_TIMEOUT_MS;
    }

    /**
     * 带超时执行附属动作（回调/告警）。
     *
     * <p>在独立虚拟线程中执行并等待给定上限；超时中断并按失败抛出。
     * 动作在 {@link JobLogHelper} 里收集的日志随结果合并进任务日志正文。</p>
     *
     * @param phase     阶段名（retry / exception / alarm）
     * @param action    动作
     * @param collected 日志正文缓冲
     * @param limitMs   执行上限毫秒
     * @throws Exception 动作失败或超时
     * @throws InterruptedException 等待被中断
     */
    private void runBounded(String phase, BoundedAction action, StringBuilder collected, long limitMs) throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> sideLog = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = Thread.ofVirtual().name("job-" + phase + "-side").unstarted(() -> {
            try {
                action.run();
            } catch (Throwable ex) {
                error.set(ex);
            } finally {
                sideLog.set(JobLogHelper.getAndClear());
            }
        });
        worker.start();
        worker.join(limitMs);
        String captured = sideLog.get();
        if (captured != null && !captured.isEmpty()) {
            collected.append(captured);
        }
        if (worker.isAlive()) {
            worker.interrupt();
            worker.join(1000L);
            throw new IllegalStateException(phase + " 附属动作超过 " + (limitMs / 1000) + "s, 已中断");
        }
        Throwable failure = error.get();
        if (failure != null) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    /**
     * 附属动作（可抛异常）。
     */
    @FunctionalInterface
    private interface BoundedAction {

        /**
         * 执行动作。
         *
         * @throws Exception 执行失败
         */
        void run() throws Exception;
    }

    /**
     * 失败告警投递（未配置目标/未接入通道时记日志，不改变任务终态）。
     *
     * <p>按台账的告警类型构造 {@link com.chua.starter.job.support.notify.JobAlarmMessage}
     * 交给告警通道；类型为空按邮件处理，保持与只有告警邮箱的历史数据兼容。</p>
     *
     * @param jobName     任务名
     * @param alarmType   告警类型：EMAIL / WEBHOOK，空按 EMAIL
     * @param alarmTarget 告警目标：邮箱（逗号分隔）或 Webhook 地址
     * @param costMs      本轮耗时
     * @param error       失败原因
     * @param collected   日志正文缓冲
     * @param timeoutSec  任务执行超时秒数（0 为不限），用于收敛附属动作上限
     */
    private void notifyAlarm(String jobName, String alarmType, String alarmTarget, long costMs, Throwable error,
                             StringBuilder collected, int timeoutSec) {
        if (alarmTarget == null || alarmTarget.isBlank()) {
            return;
        }
        String target = alarmTarget.trim();
        String type = com.chua.starter.job.support.notify.JobAlarmMessage.normalizeType(alarmType);
        if (alarmNotifier == null) {
            log.warn("[job] 失败告警未发送(未接入告警通道) job={} type={} target={}", jobName, type, target);
            collected.append("[alarm] 未接入告警通道, 未发送(").append(type).append("): ").append(target).append('\n');
            return;
        }
        com.chua.starter.job.support.notify.JobAlarmMessage message =
                new com.chua.starter.job.support.notify.JobAlarmMessage();
        message.setJobName(jobName);
        message.setAlarmType(type);
        message.setTarget(target);
        message.setCostMs(costMs);
        message.setException(error == null ? null : error.getMessage());
        message.setNodeId(nodeId);
        message.setOccurredAtMs(System.currentTimeMillis());
        try {
            runBounded("alarm", () -> alarmNotifier.notify(message), collected, sideEffectLimitMs(timeoutSec));
            collected.append("[alarm] 已通知(").append(type).append("): ").append(target).append('\n');
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            collected.append("[alarm] 通知被中断(").append(type).append("): ").append(target).append('\n');
        } catch (Throwable ex) {
            log.warn("[job] 失败告警投递失败 job={} type={} target={} cause={}",
                    jobName, type, target, ex.getMessage());
            collected.append("[alarm] 通知失败(").append(type).append("): ").append(target)
                    .append(" - ").append(ex.getMessage()).append('\n');
        }
    }

    /**
     * 单次尝试：timeoutSec>0 时在独立虚拟线程中执行并按秒等待，超时中断；否则当前线程直跑。
     *
     * <p>日志收集（{@link JobLogHelper}）与派发上下文（{@link JobDispatchContext}）都是线程私有的，
     * 因此必须在执行线程内取日志正文；台账执行参数也在这里写入上下文供处理器读取。</p>
     *
     * @param jobName      任务名
     * @param handler      处理器
     * @param manual       是否手动派发
     * @param timeoutSec   单次超时秒数，0 为不限
     * @param executeParam 台账执行参数，可为空
     * @return 单次结果
     */
    private Attempt runAttempt(String jobName, JobHandler handler, boolean manual, int timeoutSec,
                               String executeParam) {
        if (timeoutSec <= 0) {
            long start = System.currentTimeMillis();
            JobDispatchContext.begin(manual, executeParam);
            try {
                lazyInit(handler);
                handler.execute();
                return new Attempt(true, System.currentTimeMillis() - start, null, JobLogHelper.getAndClear());
            } catch (Throwable ex) {
                return new Attempt(false, System.currentTimeMillis() - start, ex, JobLogHelper.getAndClear());
            } finally {
                JobDispatchContext.end();
            }
        }
        java.util.concurrent.atomic.AtomicReference<Attempt> ref = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = Thread.ofVirtual().name("job-run-" + jobName).unstarted(() -> {
            long start = System.currentTimeMillis();
            JobDispatchContext.begin(manual, executeParam);
            try {
                lazyInit(handler);
                handler.execute();
                ref.set(new Attempt(true, System.currentTimeMillis() - start, null, JobLogHelper.getAndClear()));
            } catch (Throwable ex) {
                ref.set(new Attempt(false, System.currentTimeMillis() - start, ex, JobLogHelper.getAndClear()));
            } finally {
                JobDispatchContext.end();
            }
        });
        worker.start();
        try {
            worker.join(timeoutSec * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (worker.isAlive()) {
            worker.interrupt();
            try {
                worker.join(1000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            log.warn("[job] 执行超时已中断 job={} timeout={}s", jobName, timeoutSec);
            return new Attempt(false, timeoutSec * 1000L,
                    new java.util.concurrent.TimeoutException("执行超时 " + timeoutSec + "s，已中断"), null);
        }
        Attempt result = ref.get();
        return result == null
                ? new Attempt(false, timeoutSec * 1000L, new IllegalStateException("任务线程未产生结果"), null)
                : result;
    }

    /**
     * 拼接收集到的日志正文。
     *
     * @param collected 日志缓冲
     * @return 非空时返回文本，否则 null
     */
    private static String joinLog(StringBuilder collected) {
        return collected.length() == 0 ? null : collected.toString();
    }

    /**
     * 重试前的间隔等待。
     *
     * @param jobName 任务名（日志用）
     * @param seconds 间隔秒数，0 为不等待
     * @return 正常等待完成返回 true；被中断返回 false（中止后续重试并保留中断标记）
     */
    private static boolean sleepBeforeRetry(String jobName, int seconds) {
        if (seconds <= 0) {
            return true;
        }
        try {
            Thread.sleep(seconds * 1000L);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[job] 重试等待被中断 job={} interval={}s", jobName, seconds);
            return false;
        }
    }

    /**
     * 单次尝试结果。
     *
     * @param ok         是否成功
     * @param costMs     本次耗时毫秒
     * @param error      失败异常
     * @param logContent 本次收集的日志正文
     */
    private record Attempt(boolean ok, long costMs, Throwable error, String logContent) {
    }

    /**
     * 惰性执行 处理器 的 初始化 生命周期钩子 (每个 处理器 仅执行一次)。
     *
     * @param handler 处理器
     * @throws Exception 初始化 钩子异常
     */
    private void lazyInit(JobHandler handler) throws Exception {
        if (!initializedHandlers.add(handler.name())) {
            return;
        }
        handler.init();
        log.info("[job] 生命周期钩子 init handler={}", handler.name());
    }

    /**
     * 关闭执行器, 释放线程池, 并回调所有已 初始化 处理器 的 销毁 生命周期钩子。
     */
    public void shutdown() {
        bus.unsubscribe(JobSyncTopics.TASK_COMMAND, cmdListener);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        for (String name : initializedHandlers) {
            JobHandler handler = factory.get(name);
            if (handler == null) {
                continue;
            }
            try {
                handler.destroy();
                log.info("[job] 生命周期钩子 destroy handler={}", name);
            } catch (Throwable ex) {
                log.warn("[job] handler destroy 异常 name={} cause={}", name, ex.getMessage());
            }
        }
    }

    /**
     * 已处理任务计数。
     *
     * @return 累计数量
     */
    public long processedCount() {
        return processed.get();
    }

    /**
     * 已丢弃任务计数。
     *
     * @return 累计数量
     */
    public long droppedCount() {
        return dropped.get();
    }

    /**
     * 因并发策略被跳过的计数。
     *
     * <p>与 {@link #droppedCount()} 分开统计: 丢弃是线程池满了 (资源不足),
     * 并发跳过是上一轮还没跑完 (调度节奏与耗时不匹配), 两者的处置方向完全不同。</p>
     *
     * @return 累计数量
     */
    public long concurrentSkippedCount() {
        return concurrentSkipped.get();
    }

    /**
     * 当前正在执行中的任务名集合 (只读快照)。
     *
     * @return 正在运行的任务名集合
     */
    public java.util.Set<String> runningJobs() {
        return java.util.Set.copyOf(runningJobs);
    }

    /**
     * 默认线程池工厂。
     *
     * @return ThreadFactory
     */
    public static ThreadFactory namedThreadFactory() {
        return new ThreadFactory() {
            /**
             * 计数器
            */
            private final AtomicLong counter = new AtomicLong(0L);

            @Override
            /**
             * 新thread
            */
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, DEFAULT_THREAD_NAME_PREFIX + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
    }

    /**
     * 默认执行器: 虚拟线程执行器 (每任务一个虚拟线程, 阻塞型任务吞吐优势显著)。
     *
     * @return ExecutorService
     */
    public static ExecutorService defaultExecutor() {
        return java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 平台线程池执行器 (核心/最大/队列 + abortpolicy), 供 {@code pool.virtual=false} 时使用。
     *
     * <p>注意: {@link java.util.concurrent.LinkedBlockingQueue} 只有队列填满后才扩线程至 max,
     * 默认 队列=1000 下线程数恒等于 核心。</p>
     *
     * @return ThreadPoolExecutor
     */
    public static ThreadPoolExecutor platformExecutor() {
        return new ThreadPoolExecutor(
                DEFAULT_CORE_SIZE,
                DEFAULT_MAX_SIZE,
                60L,
                TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY),
                namedThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
