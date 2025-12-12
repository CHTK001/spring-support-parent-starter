package com.chua.starter.job.support.scheduler;

import com.chua.starter.job.support.entity.MonitorJob;
import com.chua.starter.job.support.entity.MonitorJobLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Date;

/**
 * 本地任务触发器
 * <p>
 * 负责触发本地注册的 JobHandler 执行
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
public class LocalJobTrigger {
    private static final Logger logger = LoggerFactory.getLogger(LocalJobTrigger.class);

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
        MonitorJob jobInfo = JobConfig.getInstance().loadById(jobId);
        if (jobInfo == null) {
            logger.warn(">>>>>>>>>>>> trigger fail, jobId invalid，jobId={}", jobId);
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
    private static void processTrigger(MonitorJob jobInfo, int finalFailRetryCount, TriggerTypeEnum triggerType) {

        // 1、保存日志
        MonitorJobLog jobLog = new MonitorJobLog();
        jobLog.setJobLogApp("local");
        jobLog.setJobLogTriggerBean(jobInfo.getJobExecuteBean());
        jobLog.setJobLogTriggerType(triggerType.getName());
        jobLog.setJobLogTriggerTime(new Date());
        jobLog.setJobLogTriggerDate(LocalDate.now());
        jobLog.setJobLogExecuteCode("PADDING");
        jobLog.setJobLogTriggerParam(jobInfo.getJobExecuteParam());
        JobConfig.getInstance().saveLog(jobLog);
        logger.debug(">>>>>>>>>>> job trigger start, jobId:{}", jobLog.getJobLogId());

        // 2、执行本地任务
        JobConfig.getInstance().runLocal(jobLog, jobInfo);

        logger.debug(">>>>>>>>>>> job trigger end, jobId:{}", jobLog.getJobLogId());
    }

}
