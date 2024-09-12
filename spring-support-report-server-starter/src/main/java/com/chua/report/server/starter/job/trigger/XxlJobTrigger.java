package com.chua.report.server.starter.job.trigger;

import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.report.client.starter.job.TriggerParam;
import com.chua.report.server.starter.entity.MonitorJob;
import com.chua.report.server.starter.entity.MonitorJobLog;
import com.chua.report.server.starter.job.JobConfig;
import com.chua.report.server.starter.job.TriggerTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * job trigger
 * Created by xuxueli on 17/7/13.
 */
public class XxlJobTrigger {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobTrigger.class);

    /**
     * trigger job
     *
     * @param jobId
     * @param triggerType
     * @param failRetryCount        >=0: use this param
     *                              <0: use param from job info config
     * @param executorShardingParam
     * @param executorParam         null: use job param
     *                              not null: cover job param
     */
    public static void trigger(int jobId,
                               TriggerTypeEnum triggerType,
                               int failRetryCount,
                               String executorShardingParam,
                               String executorParam) {

        // load data
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

    private static boolean isNumeric(String str) {
        try {
            int result = Integer.valueOf(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * @param jobInfo
     * @param finalFailRetryCount
     * @param triggerType
     */
    private static void processTrigger(MonitorJob jobInfo, int finalFailRetryCount, TriggerTypeEnum triggerType) {

        // 1、save log-id
        MonitorJobLog jobLog = new MonitorJobLog();
        jobLog.setJobLogApp(jobInfo.getJobApp());
        jobLog.setJobLogTriggerBean(jobInfo.getJobExecuteBean());
        jobLog.setJobLogTriggerType(triggerType.getName());
        jobLog.setJobLogProfile(jobInfo.getJobProfile());
        jobLog.setJobLogTriggerTime(new Date());
        jobLog.setJobLogTriggerDate(LocalDate.now());
        JobConfig.getInstance().saveLog(jobLog);
        logger.debug(">>>>>>>>>>> job trigger start, jobId:{}", jobLog.getJobLogId());

        // 2、init trigger-param
        TriggerParam triggerParam = new TriggerParam();
        triggerParam.setProfile(jobInfo.getJobProfile());
        triggerParam.setJobId(jobInfo.getJobId());
        triggerParam.setExecutorHandler(jobInfo.getJobExecuteBean());
        triggerParam.setExecutorParams(jobInfo.getJobExecuteParam());
        triggerParam.setExecutorRouter(jobInfo.getJobExecuteRouter());
        triggerParam.setExecutorTimeout(null == jobInfo.getJobExecuteTimeout() ? 10 : jobInfo.getJobExecuteTimeout());
        triggerParam.setLogId(jobLog.getJobLogId());
        triggerParam.setLogDateTime(jobLog.getJobLogTriggerTime().getTime());
        triggerParam.setGlueType(jobInfo.getJobGlueType());
        triggerParam.setGlueSource(jobInfo.getJobGlueSource());
        triggerParam.setGlueUpdatetime(null == jobInfo.getJobGlueUpdatetime() ? 0 : jobInfo.getJobGlueUpdatetime().getTime());

        // 3、init address
        Set<Discovery> address = JobConfig.getInstance().getAddress(triggerParam, jobInfo);

        // 4、trigger remote executor
        ReturnResult<String> triggerResult = null;
        if (address != null) {
            triggerResult = runExecutor(triggerParam, address);
        } else {
            triggerResult = ReturnResult.illegal();
        }

        // 5、collection trigger info
        String triggerMsgSb = "";

        // 6、save log trigger-info
        jobLog.setJobLogTriggerAddress(null == address ? "" :address.stream().map(it -> it.getHost() + ":" +it.getPort()).collect(Collectors.joining()));
        jobLog.setJobLogTriggerParam(jobInfo.getJobExecuteParam());
        jobLog.setJobLogTriggerCode(triggerResult.getCode());
        jobLog.setJobLogExecuteCode("PADDING");
        jobLog.setJobLogTriggerMsg(triggerMsgSb);
        JobConfig.getInstance().updateLog(jobLog);

        logger.debug(">>>>>>>>>>> job trigger end, jobId:{}", jobLog.getJobLogId());
    }

    /**
     * run executor
     *
     * @param triggerParam
     * @param address
     * @return
     */
    public static ReturnResult<String> runExecutor(TriggerParam triggerParam,  Set<Discovery> address) {
        ReturnResult<String> runResult = null;
        try {
            runResult = JobConfig.getInstance().run(address, triggerParam);
        } catch (Exception e) {
            logger.error(">>>>>>>>>>> job trigger error, please check if the executor[{}] is running.", address, e);
            runResult = ReturnResult.illegal(e);
        }

        String runResultSB = "任务运行：" + "<br>地址：" + address.stream().map(it -> it.getHost() + ":" + it.getPort()).collect(Collectors.toList()) +
                "<br>状态码：" + runResult.getCode() +
                "<br>消息：" + runResult.getMsg();

        runResult.setMsg(runResultSB);
        return runResult;
    }

}
