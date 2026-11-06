package com.chua.starter.collect.job;

import com.chua.starter.collect.service.CollectFlowService;
import com.chua.starter.job.support.annotation.Job;
import com.chua.starter.job.support.handler.JobDispatchContext;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.JobLogHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 采集宿主的本地自检任务：走 {@link Job} 注解登记的自有任务（与流程镜像行的「注入」执行器相对）。
 *
 * <p>它存在的意义是把调度链路的两端都变成可观测的：启动期由
 * {@link com.chua.starter.job.support.handler.JobAnnotationScanner} 扫描注册并写进 {@code sys_job} 台账，
 * 运行期由调度循环或作业台「立即运行」派发，正文经 {@link JobLogHelper} 落进作业日志的 message 列，
 * 所以在日志页能直接看到「本地 Bean 真的被容器注入、真的在派发线程里跑了一遍」。</p>
 *
 * <p>只读不清：只数数量、不写业务表，也不因为检出不一致就抛异常——自检失败该是一条 FAILURE 日志，
 * 而不是把宿主拖进无意义的重试。</p>
 *
 * <p>装配条件：类路径上必须有 job-starter。本类依赖的是 job 的注解与处理器契约，
 * 没有 job 时不应被装配（采集本体只依赖 schedule 包的中立端口，定时功能整体不可用）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@ConditionalOnClass(JobHandlerFactory.class)
public class CollectSelfCheckJob {

    /**
     * 任务键，同时是执行器名称。
     */
    public static final String JOB_NAME = "collect.self.check";

    /**
     * 流程服务提供者：延迟取，避免自检任务被拉进流程服务的启动期依赖环。
     */
    private final ObjectProvider<CollectFlowService> flowServiceProvider;

    /**
     * 构造本地自检任务。
     *
     * @param flowServiceProvider 流程服务
     */
    public CollectSelfCheckJob(ObjectProvider<CollectFlowService> flowServiceProvider) {
        this.flowServiceProvider = flowServiceProvider;
    }

    /**
     * 清点一次：流程数量、在册执行器、本轮触发方式。
     *
     * <p>分组与 {@code plugin.job.cluster.group} 保持一致，否则自检任务会留在 default 分组里，
     * Leader 扫描不到，等于形同虚设。</p>
     */
    @Job(value = JOB_NAME, desc = "采集宿主自检：流程与执行器清点",
            cron = "0 0/30 * * * ?", group = CollectFlowJobHandler.JOB_GROUP)
    public void check() {
        int flowCount = flowServiceProvider.getObject().listFlows(null).size();
        int handlerCount = JobHandlerFactory.getInstance().size();
        String trigger = JobDispatchContext.isManual() ? "手动" : "定时";
        JobLogHelper.log("触发方式=" + trigger
                + ", 在册执行器=" + handlerCount
                + ", 可采集流程=" + flowCount);
        log.info("[collect] 自检通过 trigger={} handlers={} flows={}", trigger, handlerCount, flowCount);
    }
}
