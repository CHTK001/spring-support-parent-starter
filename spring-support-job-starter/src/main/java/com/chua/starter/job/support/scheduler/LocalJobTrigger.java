package com.chua.starter.job.support.scheduler;

import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.entity.SysJobLog;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Date;

/**
 * 本地任务触发器
 * <p>
 * 负责触发本地注册的JobHandler执行。
 * 该类是任务执行的入口点，处理任务触发的整个流程。
 * </p>
 *
 * <h3>触发流程</h3>
 * <ol>
 *     <li>加载任务配置信息</li>
 *     <li>创建并保存任务日志记录</li>
 *     <li>调用{@link JobConfig#runLocal}执行本地任务</li>
 *     <li>更新日志执行结果</li>
 * </ol>
 *
 * <h3>参数说明</h3>
 * <ul>
 *     <li><b>failRetryCount</b> - &gt;=0使用指定值，&lt;0使用任务配置</li>
 *     <li><b>executorParam</b> - 不为null时覆盖任务配置的参数</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see JobConfig#runLocal
 * @see JobTriggerPoolHelper
 */
@Slf4j
public class LocalJobTrigger {

    /**
     * 触发任务
     *
     * @param jobId                 作业ID
     * @param triggerType           触发类型
     * @param failRetryCount        失败重试次数 >=0: 使用此参数; <0: 使用任务配置
     * @param executorShardingParam 执行器分片参数
     * @param executorParam         执行器参数 null: 使用任务参数; not null: 覆盖任务参数
     */
    public static void trigger(int jobId,
                               TriggerTypeEnum triggerType,
                               int failRetryCount,
                               String executorShardingParam,
                               String executorParam) {

        // 加载任务数据
        SysJob jobInfo = JobConfig.getInstance().loadById(jobId);
        if (jobInfo == null) {
            log.warn(">>>>>>>>>>>> 任务触发失败, 任务不存在, jobId={}", jobId);
            return;
        }
        if (executorParam != null) {
            jobInfo.setJobExecuteParam(executorParam);
        }
        int finalFailRetryCount = failRetryCount >= 0 ? failRetryCount : (null == jobInfo.getJobFailRetry() ? 3 : jobInfo.getJobFailRetry());

        processTrigger(jobInfo, finalFailRetryCount, triggerType);
    }

    /**
     * 处理触发
     *
     * @param jobInfo            任务信息
     * @param finalFailRetryCount 最终失败重试次数
     * @param triggerType        触发类型
     */
    private static void processTrigger(SysJob jobInfo, int finalFailRetryCount, TriggerTypeEnum triggerType) {

        // 1、保存日志
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobLogApp("local");
        jobLog.setJobLogTriggerBean(jobInfo.getJobExecuteBean());
        jobLog.setJobLogTriggerType(triggerType.getName());
        jobLog.setJobLogTriggerTime(new Date());
        jobLog.setJobLogTriggerDate(LocalDate.now());
        jobLog.setJobLogExecuteCode("PADDING");
        jobLog.setJobLogTriggerParam(jobInfo.getJobExecuteParam());
        JobConfig.getInstance().saveLog(jobLog);
        log.debug(">>>>>>>>>>> 任务触发开始, 日志ID={}", jobLog.getJobLogId());

        // 2、执行本地任务
        JobConfig.getInstance().runLocal(jobLog, jobInfo);

        log.debug(">>>>>>>>>>> 任务触发完成, 日志ID={}", jobLog.getJobLogId());
    }

}
