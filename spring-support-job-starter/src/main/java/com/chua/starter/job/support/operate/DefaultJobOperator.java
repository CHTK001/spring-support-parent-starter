package com.chua.starter.job.support.operate;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.task.scheduler.Trigger;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.dispatch.JobQuery;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.dispatch.JobRuntime;
import com.chua.starter.job.support.dispatch.JobSpec;
import com.chua.starter.job.support.dispatch.JobTriggering;
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
        if (spec == null || spec.getJobKey() == null || spec.getJobKey().isBlank()) {
            throw new IllegalArgumentException("任务键不能为空");
        }
        requireSchedulable(spec.getScheduleType(), spec.getScheduleTime());
        return repository.save(spec);
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
        bus.publish(JobSyncTopics.TASK_COMMAND, nodeId, key);
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
