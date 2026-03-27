package com.chua.starter.job.support.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.job.support.GlueTypeEnum;
import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.callback.JobExecutionCallback;
import com.chua.starter.job.support.callback.JobExecutionCallbackContext;
import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.entity.SysJobLog;
import com.chua.starter.job.support.glue.GlueFactory;
import com.chua.starter.job.support.handler.GlueJobHandler;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.handler.ScriptJobHandler;
import com.chua.starter.job.support.log.JobDetailLogger;
import com.chua.starter.job.support.log.JobFileAppender;
import com.chua.starter.job.support.log.JobLogDetailService;
import com.chua.starter.job.support.mapper.SysJobLogMapper;
import com.chua.starter.job.support.mapper.SysJobMapper;
import com.chua.starter.job.support.thread.JobContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 作业配置管理器
 * <p>
 * 采用单例模式，负责管理任务调度的核心配置和数据访问。
 * 主要功能包括：
 * <ul>
 *     <li>管理JobProperties配置</li>
 *     <li>提供任务和日志的数据库操作</li>
 *     <li>提供本地任务执行能力</li>
 *     <li>提供分布式锁支持（当前为本地锁实现）</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Slf4j
public class JobConfig {

    /**
     * 单例实例
     */
    private static final JobConfig INSTANCE = new JobConfig();

    /**
     * 任务配置属性
     */
    private JobProperties jobProperties;

    /**
     * Spring应用上下文
     */
    private ApplicationContext applicationContext;

    /**
     * 任务Mapper
     */
    private SysJobMapper sysJobMapper;

    /**
     * 任务日志Mapper
     */
    private SysJobLogMapper sysJobLogMapper;

    /**
     * 本地锁，用于保证调度线程安全（单机版本）
     */
    private final Map<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，防止外部实例化
     */
    private JobConfig() {
    }

    /**
     * 获取单例实例
     *
     * @return JobConfig实例
     */
    public static JobConfig getInstance() {
        return INSTANCE;
    }

    /**
     * 注册任务配置属性
     *
     * @param jobProperties 任务配置属性
     */
    public void register(JobProperties jobProperties) {
        this.jobProperties = jobProperties;
        log.info("JobConfig: 任务配置属性已注册");
    }

    /**
     * 获取快速最大触发池
     *
     * @return int
     */
    public int getTriggerPoolFastMax() {
        return null == jobProperties ? 200 : jobProperties.getTriggerPoolFastMax();
    }

    /**
     * 获取触发池慢速最大值
     *
     * @return int
     */
    public int getTriggerPoolSlowMax() {
        return null == jobProperties ? 100 : jobProperties.getTriggerPoolSlowMax();
    }

    /**
     * 通过作业ID加载监控作业。
     *
     * @param jobId 作业ID
     * @return 返回SysJob对象，如果找不到则返回null。
     */
    public SysJob loadById(int jobId) {
        return requireJobMapper().selectById(jobId);
    }

    /**
     * 注册应用上下文。
     *
     * @param applicationContext 应用上下文实例
     */
    public void register(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.sysJobMapper = applicationContext.getBean(SysJobMapper.class);
        this.sysJobLogMapper = applicationContext.getBean(SysJobLogMapper.class);
        log.info("JobConfig: Spring应用上下文已注册");
    }

    /**
     * 保存监控作业日志。
     *
     * @param jobLog 监控作业日志对象
     */
    public void saveLog(SysJobLog jobLog) {
        requireJobLogMapper().insert(jobLog);
    }

    /**
     * 更新监控作业日志。
     *
     * @param jobLog 监控作业日志对象
     */
    public void updateLog(SysJobLog jobLog) {
        requireJobLogMapper().updateById(jobLog);
    }

    /**
     * 执行本地任务
     *
     * @param jobLog    任务日志
     * @param jobInfo   任务信息
     * @return 执行结果
     */
    public void runLocal(SysJobLog jobLog, SysJob jobInfo) {
        JobHandler handler = resolveHandler(jobInfo);
        String handlerName = resolveHandlerName(jobInfo);

        if (handler == null) {
            log.warn("任务处理器未找到: {}", handlerName);
            jobLog.setJobLogExecuteCode("FAILURE");
            jobLog.setJobLogTriggerMsg("处理器未找到: " + handlerName);
            updateLog(jobLog);
            return;
        }

        JobContext context = new JobContext(
                jobLog.getJobLogId(),
                jobInfo.getJobId(),
                jobInfo.getJobNo(),
                jobLog.getJobLogNo(),
                jobInfo.getJobExecuteParam(),
                resolveLogFilePath(jobLog),
                0,
                1
        );
        context.getAttributes().put("handler", handlerName);
        context.getAttributes().put("profile", resolveProfile());
        context.getAttributes().put("dispatchMode", StringUtils.hasText(jobInfo.getJobDispatchMode())
                ? jobInfo.getJobDispatchMode().trim()
                : JobDispatchModeEnum.LOCAL.name());
        context.getAttributes().put("address", trimToNull(jobInfo.getJobRemoteExecutorAddress()));
        JobContext.setJobContext(context);

        long startTime = System.currentTimeMillis();
        int maxAttempts = Math.max(1, resolveRetryTimes(jobInfo));
        Throwable lastError = null;
        boolean success = false;
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                context.getAttributes().put("attempt", attempt);
                context.getAttributes().put("maxAttempts", maxAttempts);
                JobDetailLogger.info("开始执行任务，handler=" + handlerName + "，attempt=" + attempt + "/" + maxAttempts,
                        attempt == 1 ? "START" : "RETRY", resolveAttemptProgress(attempt, maxAttempts));
                if (attempt > 1) {
                    invokeRetryCallback(jobInfo, jobLog, context, lastError, attempt, maxAttempts);
                }
                try {
                    executeHandler(handler, context, jobInfo.getJobExecuteTimeout());
                    success = true;
                    if (!StringUtils.hasText(context.getHandleMsg())) {
                        context.setSuccess("执行成功");
                    }
                    break;
                } catch (Throwable e) {
                    lastError = unwrap(e);
                    context.setFail("执行异常: " + safeMessage(lastError));
                    JobDetailLogger.error("任务执行异常，handler=" + handlerName + "，attempt=" + attempt, lastError);
                    if (attempt >= maxAttempts) {
                        invokeExceptionCallback(jobInfo, jobLog, context, lastError, attempt, maxAttempts);
                        break;
                    }
                    sleepRetryInterval(jobInfo.getJobRetryInterval());
                }
            }

            long cost = System.currentTimeMillis() - startTime;
            jobLog.setJobLogCost(BigDecimal.valueOf(cost));
            if (success && context.getHandleCode() == JobContext.HANDLE_CODE_SUCCESS) {
                jobLog.setJobLogExecuteCode("SUCCESS");
                jobLog.setJobLogTriggerMsg(buildResultMessage(context, "执行成功，耗时: " + cost + "ms"));
                JobDetailLogger.info("任务执行完成，状态=SUCCESS，耗时=" + cost + "ms", "END", 100);
                log.info("本地任务执行成功: {}, jobNo={}, 耗时: {}ms", handlerName, jobInfo.getJobNo(), cost);
            } else if (context.getHandleCode() == JobContext.HANDLE_CODE_TIMEOUT) {
                jobLog.setJobLogExecuteCode("FAILURE");
                jobLog.setJobLogTriggerMsg(buildResultMessage(context, "执行超时，耗时: " + cost + "ms"));
                JobDetailLogger.warn("任务执行完成，状态=TIMEOUT，耗时=" + cost + "ms");
                log.warn("本地任务执行超时: {}, jobNo={}, 耗时: {}ms", handlerName, jobInfo.getJobNo(), cost);
            } else {
                jobLog.setJobLogExecuteCode("FAILURE");
                jobLog.setJobLogTriggerMsg(buildResultMessage(context, "执行失败，耗时: " + cost + "ms"));
                JobDetailLogger.warn("任务执行完成，状态=FAILURE，耗时=" + cost + "ms");
                log.error("本地任务执行失败: {}, jobNo={}, 耗时: {}ms, 错误: {}",
                        handlerName, jobInfo.getJobNo(), cost, safeMessage(lastError));
            }
        } finally {
            updateLog(jobLog);
            JobContext.removeJobContext();
        }
    }

    /**
     * 本地锁
     *
     * @param name 名称
     * @return JobLock
     */
    public JobLock lock(String name) {
        String lockName = (name == null || name.trim().isEmpty()) ? "default" : name.trim();
        return new LocalJobLock(localLocks.computeIfAbsent(lockName, key -> new ReentrantLock()));
    }

    /**
     * 查询待调度任务
     *
     * @param nextTime      下次触发时间
     * @param preReadCount  预读数量
     * @return 任务列表
     */
    public List<SysJob> scheduleJobQuery(long nextTime, int preReadCount) {
        return requireJobMapper().selectPage(
                new Page<>(1, Math.max(preReadCount, 1)),
                Wrappers.<SysJob>lambdaQuery()
                        .eq(SysJob::getJobTriggerStatus, 1)
                        .and(wrapper -> wrapper.isNull(SysJob::getJobDispatchMode)
                                .or()
                                .eq(SysJob::getJobDispatchMode, JobDispatchModeEnum.LOCAL.name()))
                        .le(SysJob::getJobTriggerNextTime, nextTime)
                        .orderByDesc(SysJob::getJobId)
        ).getRecords();
    }

    /**
     * 更新任务调度信息
     *
     * @param jobInfo 任务信息
     */
    public void scheduleUpdate(SysJob jobInfo) {
        requireJobMapper().updateById(jobInfo);
    }

    private SysJobMapper requireJobMapper() {
        if (sysJobMapper == null) {
            throw new IllegalStateException("JobConfig 尚未完成初始化: SysJobMapper 不可用");
        }
        return sysJobMapper;
    }

    private SysJobLogMapper requireJobLogMapper() {
        if (sysJobLogMapper == null) {
            throw new IllegalStateException("JobConfig 尚未完成初始化: SysJobLogMapper 不可用");
        }
        return sysJobLogMapper;
    }

    public JobLogDetailService jobLogDetailService() {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(JobLogDetailService.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JobHandler resolveHandler(SysJob jobInfo) {
        GlueTypeEnum glueType = GlueTypeEnum.match(jobInfo.getJobGlueType());
        if (glueType == null || glueType == GlueTypeEnum.BEAN) {
            return JobHandlerFactory.getInstance().get(jobInfo.getJobExecuteBean());
        }
        try {
            long glueUpdatetime = jobInfo.getJobGlueUpdatetime() == null
                    ? System.currentTimeMillis()
                    : jobInfo.getJobGlueUpdatetime().getTime();
            if (glueType.isScript()) {
                return new ScriptJobHandler(
                        jobInfo.getJobId(),
                        jobInfo.getJobNo(),
                        glueUpdatetime,
                        jobInfo.getJobGlueSource(),
                        glueType
                );
            }
            return new GlueJobHandler(GlueFactory.getInstance().loadNewInstance(jobInfo.getJobGlueSource()), glueUpdatetime);
        } catch (Exception e) {
            log.error("构建任务处理器失败, jobId={}, jobNo={}", jobInfo.getJobId(), jobInfo.getJobNo(), e);
            return null;
        }
    }

    private void executeHandler(JobHandler handler, JobContext context, Integer timeoutSeconds) throws Throwable {
        int safeTimeout = Math.max(0, timeoutSeconds == null ? 0 : timeoutSeconds);
        if (safeTimeout <= 0) {
            handler.execute();
            return;
        }
        FutureTask<Boolean> futureTask = new FutureTask<>(() -> {
            JobContext.setJobContext(context);
            handler.execute();
            return true;
        });
        Thread futureThread = new Thread(futureTask, "job-local-executor-" + context.getJobNo());
        futureThread.start();
        try {
            futureTask.get(safeTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            context.setTimeout("执行超时，限制 " + safeTimeout + " 秒");
            JobDetailLogger.warn("任务执行超时，限制 " + safeTimeout + " 秒");
            throw e;
        } finally {
            futureThread.interrupt();
        }
    }

    private void invokeRetryCallback(SysJob jobInfo,
                                     SysJobLog jobLog,
                                     JobContext context,
                                     Throwable throwable,
                                     int attempt,
                                     int maxAttempts) {
        invokeCallback(jobInfo.getJobRetryCallbackBean(), "RETRY", jobInfo, jobLog, context, throwable, attempt, maxAttempts);
    }

    private void invokeExceptionCallback(SysJob jobInfo,
                                         SysJobLog jobLog,
                                         JobContext context,
                                         Throwable throwable,
                                         int attempt,
                                         int maxAttempts) {
        invokeCallback(jobInfo.getJobExceptionCallbackBean(), "EXCEPTION", jobInfo, jobLog, context, throwable, attempt, maxAttempts);
    }

    private void invokeCallback(String callbackBean,
                                String phase,
                                SysJob jobInfo,
                                SysJobLog jobLog,
                                JobContext context,
                                Throwable throwable,
                                int attempt,
                                int maxAttempts) {
        String beanName = trimToNull(callbackBean);
        if (!StringUtils.hasText(beanName)) {
            return;
        }
        try {
            context.getAttributes().put("callbackPhase", phase);
            context.getAttributes().put("callbackThrowable", throwable);
            if (applicationContext != null && applicationContext.containsBean(beanName)) {
                Object bean = applicationContext.getBean(beanName);
                if (bean instanceof JobExecutionCallback callback) {
                    callback.onCallback(JobExecutionCallbackContext.builder()
                            .phase(phase)
                            .job(jobInfo)
                            .jobLog(jobLog)
                            .jobContext(context)
                            .attempt(attempt)
                            .maxAttempts(maxAttempts)
                            .throwable(throwable)
                            .build());
                    JobDetailLogger.info("已执行任务回调: " + beanName + ", phase=" + phase);
                    return;
                }
            }
            JobHandler handler = JobHandlerFactory.getInstance().get(beanName);
            if (handler != null) {
                handler.execute();
                JobDetailLogger.info("已执行任务处理器回调: " + beanName + ", phase=" + phase);
                return;
            }
            JobDetailLogger.warn("未找到任务回调处理器: " + beanName + ", phase=" + phase);
        } catch (Throwable e) {
            log.warn("任务回调执行失败, callbackBean={}, phase={}, jobNo={}", beanName, phase, jobInfo.getJobNo(), e);
            JobDetailLogger.error("任务回调执行失败: " + beanName + ", phase=" + phase, e);
        }
    }

    private void sleepRetryInterval(Integer retryIntervalSeconds) {
        int seconds = Math.max(0, retryIntervalSeconds == null ? 0 : retryIntervalSeconds);
        if (seconds <= 0) {
            return;
        }
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int resolveRetryTimes(SysJob jobInfo) {
        return Math.max(0, jobInfo.getJobFailRetry() == null ? 0 : jobInfo.getJobFailRetry()) + 1;
    }

    private int resolveAttemptProgress(int attempt, int maxAttempts) {
        return Math.min(99, Math.max(1, (int) Math.round((attempt * 100.0d) / Math.max(maxAttempts, 1))));
    }

    private String resolveHandlerName(SysJob jobInfo) {
        GlueTypeEnum glueType = GlueTypeEnum.match(jobInfo.getJobGlueType());
        if (glueType == null || glueType == GlueTypeEnum.BEAN) {
            return trimToNull(jobInfo.getJobExecuteBean());
        }
        return glueType.name();
    }

    private String resolveProfile() {
        if (applicationContext == null || applicationContext.getEnvironment() == null) {
            return null;
        }
        String[] activeProfiles = applicationContext.getEnvironment().getActiveProfiles();
        if (activeProfiles == null || activeProfiles.length == 0) {
            return null;
        }
        return trimToNull(activeProfiles[0]);
    }

    private String resolveLogFilePath(SysJobLog jobLog) {
        String path = trimToNull(jobLog.getJobLogFilePath());
        if (StringUtils.hasText(path)) {
            return path;
        }
        return JobFileAppender.makeLogFileName(jobLog.getJobLogTriggerTime(), jobLog.getJobNo(), jobLog.getJobLogNo());
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知异常";
        }
        String message = trimToNull(throwable.getMessage());
        return StringUtils.hasText(message) ? message : throwable.getClass().getSimpleName();
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.ExecutionException && throwable.getCause() != null) {
            return unwrap(throwable.getCause());
        }
        if (throwable instanceof java.lang.reflect.InvocationTargetException && throwable.getCause() != null) {
            return unwrap(throwable.getCause());
        }
        return throwable;
    }

    private String buildResultMessage(JobContext context, String defaultMessage) {
        String message = context == null ? null : trimToNull(context.getHandleMsg());
        if (StringUtils.hasText(message)) {
            return message;
        }
        return defaultMessage;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 本地锁实现
     */
    public static class LocalJobLock implements JobLock {
        private final ReentrantLock lock;

        public LocalJobLock(ReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public void lock() {
            lock.lock();
        }

        @Override
        public void unlock() {
            lock.unlock();
        }
    }

    /**
     * 锁接口
     */
    public interface JobLock {
        void lock();
        void unlock();
    }
}
