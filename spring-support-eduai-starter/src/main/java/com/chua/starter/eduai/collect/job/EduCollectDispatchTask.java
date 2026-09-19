package com.chua.starter.eduai.collect.job;

import com.chua.starter.eduai.service.IEduCollectTaskService;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 题库采集调度派发任务。
 * <p>
 * 经 job-starter 的注解扫描自动注册进 sys_job（键 edu-collect-dispatch，SINGLE 模式），
 * 可被 job-platform / job-console 启停。每 20 秒扫描 edu_collect_task 中
 * 已启用且 next_run_time 到期的任务并执行采集。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EduCollectDispatchTask {

    /**
     * 采集任务服务
     */
    private final IEduCollectTaskService collectTaskService;

    /**
     * 到期任务派发。
     */
    @Job(value = "edu-collect-dispatch", fixedDelay = 20_000L,
            desc = "题库采集调度派发：扫描 edu_collect_task 中到期的启用任务并执行采集入库")
    public void dispatch() {
        try {
            collectTaskService.dispatchDueTasks();
        } catch (Exception e) {
            log.error("edu-collect-dispatch 派发异常", e);
        }
    }
}
