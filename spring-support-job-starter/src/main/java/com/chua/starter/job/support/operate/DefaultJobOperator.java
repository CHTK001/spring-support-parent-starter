package com.chua.starter.job.support.operate;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.task.scheduler.Trigger;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.dispatch.JobQuery;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.dispatch.JobRuntime;
import com.chua.starter.job.support.dispatch.JobSpec;
import com.chua.starter.job.support.dispatch.JobTriggering;
import com.chua.starter.job.support.glue.GlueTypes;
import com.chua.starter.job.support.glue.compile.SourceCoordinates;
import com.chua.starter.job.support.sync.JobSyncBus;
import com.chua.starter.job.support.sync.JobSyncTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * {@link JobOperator} 默认实现：调度配置校验 + 仓库写入 + 触发器缓存失效 + 总线派发。
 *
 * <p>校验直接复用运行期真正使用的 {@link JobTriggering}，而不是另找一套 cron 解析器：
 * 只有"当前这套触发器能算出下一拍"的配置才算合法，避免出现接口收得下、调度器跑不动的表达式。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultJobOperator implements JobOperator {

    /**
     * 脚本正文长度上限（字符）：正文随派发命令单行传输，过大既不安全也不必要
     */
    private static final int MAX_GLUE_SOURCE_CHARS = 64_000;

    /**
     * 任务仓库
     */
    private final JobRepository repository;

    /**
     * 进程内/跨节点事件总线
     */
    private final JobSyncBus bus;

    /**
     * 当前节点标识
     */
    private final String nodeId;

    @Override
    /**
     * 登记或更新任务
    */
    public boolean declare(JobSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("任务规格不能为空");
        }
        if (spec.getJobKey() == null || spec.getJobKey().isBlank()) {
            // 脚本任务不要求用户填任务键：用 IdUtils 生成全局唯一标识（snowflake）
            if (isScript(spec.getGlueType())) {
                spec.setJobKey(JobSpec.SCRIPT_JOB_KEY_PREFIX + IdUtils.createSnowflakeId());
            } else {
                throw new IllegalArgumentException("任务键不能为空");
            }
        }
        requireGlueValid(spec);
        requireSchedulable(spec.getScheduleType(), spec.getScheduleTime());
        return repository.save(spec);
    }

    /**
     * 是否片段任务（执行类型非 BEAN，含脚本与 Java 代码片段）。
     *
     * @param glueType 执行类型
     * @return 片段任务返回 true
     */
    private static boolean isScript(String glueType) {
        return GlueTypes.isGlue(glueType);
    }

    /**
     * 校验执行类型与片段正文：片段任务必须带正文，且正文不超过下发通道可承受的大小。
     *
     * <p>{@code GLUE_JAVA} 额外做一次「能不能认出主类」的静态校验——这一层不编译，
     * 只挡住明显不是类源码的正文（例如直接粘了一段 Groovy），让用户在保存时看到错误，
     * 而不是等到第一拍派发才在执行器上炸。</p>
     *
     * @param spec 任务规格
     * @throws IllegalArgumentException 片段正文缺失、过大或 Java 片段缺 class 声明
     */
    private void requireGlueValid(JobSpec spec) {
        if (!isScript(spec.getGlueType())) {
            return;
        }
        if (spec.getGlueSource() == null || spec.getGlueSource().isBlank()) {
            throw new IllegalArgumentException("片段任务必须填写片段正文");
        }
        if (spec.getGlueSource().length() > MAX_GLUE_SOURCE_CHARS) {
            throw new IllegalArgumentException("片段正文过长(上限 " + MAX_GLUE_SOURCE_CHARS + " 字符): "
                    + spec.getGlueSource().length());
        }
        if (!GlueTypes.isJava(spec.getGlueType())) {
            return;
        }
        try {
            SourceCoordinates.of(spec.getGlueSource());
        } catch (IllegalStateException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    @Override
    /**
     * 改调度配置
    */
    public boolean updateSchedule(String jobKey, String scheduleType, String scheduleTime) {
        requireSchedulable(scheduleType, scheduleTime);
        return repository.updateSchedule(jobKey, scheduleType, scheduleTime);
    }

    @Override
    /**
     * 启停自动调度
    */
    public boolean changeStatus(String jobKey, boolean enabled) {
        return repository.changeStatus(jobKey, enabled);
    }

    @Override
    /**
     * 立即派发一次
    */
    public boolean trigger(String jobKey) {
        if (jobKey == null || jobKey.isBlank() || repository.find(jobKey) == null) {
            return false;
        }
        String key = jobKey.trim();
        // 手动派发同样携带台账上的重试/超时策略与脚本正文，执行器才能按配置生效
        JobRuntime runtime = repository.runtime(key);
        Integer failRetry = runtime == null ? null : runtime.getFailRetry();
        Integer executeTimeout = runtime == null ? null : runtime.getExecuteTimeout();
        bus.publish(JobSyncTopics.TASK_COMMAND, nodeId,
                JobSyncTopics.command(key, true, failRetry,
                        runtime == null ? null : runtime.getRetryInterval(),
                        executeTimeout,
                        runtime == null ? null : runtime.getGlueType(),
                        // 手动触发兜底携带正文：即使 job-glue 推送失败/单机无推送，手动执行也一定跑得起来
                        runtime == null ? null : runtime.getGlueSource(),
                        runtime == null ? null : runtime.getGlueUpdatetimeMs(),
                        runtime == null ? null : runtime.getRetryCallbackBean(),
                        runtime == null ? null : runtime.getExceptionCallbackBean(),
                        runtime == null ? null : runtime.getAlarmEmail(),
                        runtime == null ? null : runtime.getAlarmType(),
                        runtime == null ? null : runtime.getExecuteParam()));
        log.info("[job] 手动派发已投递 job={} nodeId={}", key, nodeId);
        return true;
    }

    @Override
    /**
     * 删除任务
    */
    public boolean remove(String jobKey) {
        return repository.remove(jobKey);
    }

    @Override
    /**
     * 读取运行态
    */
    public JobRuntime runtime(String jobKey) {
        return repository.runtime(jobKey);
    }

    @Override
    /**
     * 按分组列出运行态
    */
    public List<JobRuntime> list(String group) {
        return repository.listGroup(group);
    }

    @Override
    /**
     * 运行态分页
    */
    public PageResult<JobRuntime> page(JobQuery query) {
        return repository.page(query);
    }

    /**
     * 校验调度配置能被运行期触发器接受。
     *
     * @param scheduleType 调度类型
     * @param scheduleTime 调度配置
     * @throws IllegalArgumentException 表达式非法或算不出下一拍
     */
    private void requireSchedulable(String scheduleType, String scheduleTime) {
        String time = scheduleTime == null ? "" : scheduleTime.trim();
        if (time.isEmpty() || JobSpec.TYPE_NONE.equalsIgnoreCase(scheduleType)) {
            return;
        }
        JobDefinition probe = new JobDefinition();
        probe.setName("__validate__");
        if (JobSpec.TYPE_DELAY.equalsIgnoreCase(scheduleType)) {
            probe.setFixedDelayMs(parseMs(time, scheduleType));
        } else if (JobSpec.TYPE_FIXED.equalsIgnoreCase(scheduleType)) {
            probe.setFixedRateMs(parseMs(time, scheduleType));
        } else {
            probe.setCron(time);
        }
        Trigger trigger;
        try {
            trigger = JobTriggering.createTrigger(probe);
        } catch (Exception ex) {
            throw new IllegalArgumentException("非法调度配置 [" + scheduleType + "=" + time + "]: "
                    + ex.getMessage(), ex);
        }
        if (trigger == null || JobTriggering.nextFireMs(probe, trigger, 0L, System.currentTimeMillis()) < 0L) {
            throw new IllegalArgumentException("非法调度配置 [" + scheduleType + "=" + time + "]: 无法算出下一次触发时间");
        }
    }

    /**
     * 毫秒间隔解析, 非法直接拒绝。
     *
     * @param value        字串
     * @param scheduleType 调度类型 (仅用于报错文案)
     * @return 毫秒数
     */
    private long parseMs(String value, String scheduleType) {
        try {
            long ms = Long.parseLong(value);
            if (ms <= 0L) {
                throw new NumberFormatException("必须为正数");
            }
            return ms;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("调度类型 " + scheduleType + " 需要正毫秒数, 实际=" + value, ex);
        }
    }
}
