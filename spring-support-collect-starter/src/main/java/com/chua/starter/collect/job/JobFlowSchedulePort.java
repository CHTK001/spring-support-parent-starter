package com.chua.starter.collect.job;

import com.chua.starter.collect.schedule.CollectFlowScheduleKeys;
import com.chua.starter.collect.schedule.CollectFlowSchedulePort;
import com.chua.starter.collect.schedule.CollectFlowScheduleSpec;
import com.chua.starter.collect.schedule.CollectFlowScheduleState;
import com.chua.starter.collect.service.CollectExecutionService;
import com.chua.starter.job.support.dispatch.JobRuntime;
import com.chua.starter.job.support.dispatch.JobSpec;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.operate.JobOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于 job-starter 台账的排期实现。
 *
 * <p>每个流程在 sys_job 里占一行（键 {@code collect-flow-<流程ID>}、分组 {@code collect-flow}）：
 * 登记时同时把 {@link CollectFlowJobHandler} 注册进 {@code JobHandlerFactory}，
 * 到点由 job-starter 直接派发；cron 为空时写成"只登记不派发"，让"能不能定时跑"在台账里一眼可查。</p>
 *
 * <p>本类是采集模块里唯一依赖 job-starter 的排期出口，只有容器里存在
 * {@link JobOperator} 时才会被装配（见 CollectScheduleConfiguration），
 * 因此类路径上没有 job-starter 时这个类不会被加载。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RequiredArgsConstructor
public class JobFlowSchedulePort implements CollectFlowSchedulePort {

    /**
     * job-starter 调度台账入口。
     */
    private final JobOperator jobOperator;

    /**
     * 执行服务，派发时才取，避免与执行服务在启动期互相依赖。
     */
    private final ObjectProvider<CollectExecutionService> executionServiceProvider;

    @Override
    public boolean schedulingAvailable() {
        return true;
    }

    @Override
    public void declare(CollectFlowScheduleSpec spec) {
        Integer flowId = spec.flowId();
        if (flowId == null) {
            return;
        }
        String jobKey = CollectFlowScheduleKeys.jobKeyOf(flowId);
        JobHandlerFactory.getInstance().register(
                new CollectFlowJobHandler(flowId, spec.flowName(), executionServiceProvider));
        JobSpec jobSpec = new JobSpec();
        jobSpec.setJobKey(jobKey);
        jobSpec.setJobNo(jobKey);
        jobSpec.setJobName(spec.flowName());
        jobSpec.setDesc("数据采集流程: " + spec.flowName());
        jobSpec.setGroup(CollectFlowScheduleKeys.JOB_GROUP);
        jobSpec.setScheduleType(spec.cron().isEmpty() ? JobSpec.TYPE_NONE : JobSpec.TYPE_CRON);
        jobSpec.setScheduleTime(spec.cron());
        jobSpec.setEnabled(spec.enabled());
        jobSpec.setExecuteParam(String.valueOf(flowId));
        jobOperator.declare(jobSpec);
    }

    @Override
    public void remove(String jobKey) {
        jobOperator.remove(jobKey);
        JobHandlerFactory.getInstance().deregister(jobKey);
    }

    @Override
    public boolean changeStatus(String jobKey, boolean enabled) {
        return jobOperator.changeStatus(jobKey, enabled);
    }

    @Override
    public CollectFlowScheduleState runtime(String jobKey) {
        JobRuntime runtime = jobOperator.runtime(jobKey);
        return runtime == null ? null : toState(runtime);
    }

    @Override
    public Map<String, CollectFlowScheduleState> listRuntimes() {
        Map<String, CollectFlowScheduleState> states = new HashMap<>();
        try {
            for (JobRuntime runtime : jobOperator.list(CollectFlowScheduleKeys.JOB_GROUP)) {
                states.put(runtime.getJobKey(), toState(runtime));
            }
        } catch (RuntimeException ex) {
            log.warn("[collect] 读取调度台账失败，下次触发时间暂不展示: {}", ex.getMessage());
        }
        return states;
    }

    @Override
    public void removeLegacyTick() {
        // 旧的单条轮询心跳已被"每流程一行 sys_job"取代，残留行会让任务中心显示一个永不派发的僵尸任务
        jobOperator.remove("collect.scheduler.tick");
    }

    /**
     * job 台账运行态转本模块的中立快照。
     *
     * @param runtime job 台账运行态
     * @return 中立快照
     */
    private CollectFlowScheduleState toState(JobRuntime runtime) {
        return new CollectFlowScheduleState(runtime.getJobKey(), runtime.getNextFireMs(),
                runtime.getLastFireMs(), runtime.isHandlerRegistered());
    }
}
