package com.chua.starter.eduai.collect.job;

import com.chua.starter.eduai.service.IEduCollectTaskService;
import com.chua.starter.job.support.handler.JobHandler;
import lombok.RequiredArgsConstructor;

/**
 * 采集任务的调度执行器：一个采集任务对应一个处理器实例, 名称为 {@code edu-collect-<taskId>}。
 *
 * <p>处理器只携带任务标识, 执行时才回表读取最新配置, 因此改了脚本/学科不需要重新注册,
 * 也不会出现"用启动期的旧快照采集"的情况。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RequiredArgsConstructor
public class EduCollectJobHandler implements JobHandler {

    /**
     * 采集任务在 sys_job 中的键前缀
     */
    public static final String JOB_KEY_PREFIX = "edu-collect-";

    /**
     * 采集任务分组
     */
    public static final String JOB_GROUP = "edu-collect";

    /**
     * 采集任务标识
     */
    private final Long taskId;

    /**
     * 任务显示名 (仅用于描述文案)
     */
    private final String jobName;

    /**
     * 采集任务服务
     */
    private final IEduCollectTaskService collectTaskService;

    /**
     * 推导采集任务对应的调度任务键。
     *
     * @param taskId 采集任务标识
     * @return 调度任务键
     */
    public static String jobKeyOf(Long taskId) {
        return JOB_KEY_PREFIX + taskId;
    }

    @Override
    public String name() {
        return jobKeyOf(taskId);
    }

    @Override
    public String desc() {
        return "题库采集任务: " + jobName;
    }

    @Override
    public void execute() {
        collectTaskService.runScheduled(taskId);
    }
}
