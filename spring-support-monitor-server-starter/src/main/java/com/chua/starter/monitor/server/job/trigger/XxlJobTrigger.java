package com.chua.starter.monitor.server.job.trigger;

import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.job.TriggerParam;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorJob;
import com.chua.starter.monitor.server.entity.MonitorJobLog;
import com.chua.starter.monitor.server.job.JobConfig;
import com.chua.starter.monitor.server.job.TriggerTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * xxl-job trigger
 * Created by xuxueli on 17/7/13.
 */
public class XxlJobTrigger {
    private static Logger logger = LoggerFactory.getLogger(XxlJobTrigger.class);

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
        jobLog.setJobLogTriggerBean(jobInfo.getJobBean());
        jobLog.setJobLogTriggerType(triggerType.getName());
        jobLog.setJobLogProfile(jobInfo.getJobProfile());
        jobLog.setJobLogTriggerTime(new Date());
        JobConfig.getInstance().saveLog(jobLog);
        logger.debug(">>>>>>>>>>> xxl-job trigger start, jobId:{}", jobLog.getJobLogId());

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
        List<MonitorRequest> address = JobConfig.getInstance().getAddress(triggerParam, jobInfo);

        // 4、trigger remote executor
        ReturnResult<String> triggerResult = null;
        if (address != null) {
            triggerResult = runExecutor(triggerParam, address);
        } else {
            triggerResult = ReturnResult.illegal();
        }

        // 5、collection trigger info
        StringBuffer triggerMsgSb = new StringBuffer();

        // 6、save log trigger-info
        jobLog.setJobLogTriggerAddress(Json.toJson(address));
        jobLog.setJobLogTriggerParam(jobInfo.getJobExecuteParam());
        jobLog.setJobLogTriggerCode(triggerResult.getCode());
        jobLog.setJobLogTriggerMsg(triggerMsgSb.toString());
        JobConfig.getInstance().updateLog(jobLog);

        logger.debug(">>>>>>>>>>> xxl-job trigger end, jobId:{}", jobLog.getJobLogId());
    }

    /**
     * run executor
     *
     * @param triggerParam
     * @param address
     * @return
     */
    public static ReturnResult<String> runExecutor(TriggerParam triggerParam,  List<MonitorRequest> address) {
        ReturnResult<String> runResult = null;
        try {
            runResult = JobConfig.getInstance().run(address, triggerParam);
        } catch (Exception e) {
            logger.error(">>>>>>>>>>> xxl-job trigger error, please check if the executor[{}] is running.", address, e);
            runResult = ReturnResult.illegal(e);
        }

        StringBuffer runResultSB = new StringBuffer("任务运行：");
        runResultSB.append("<br>地址：").append(address.stream().map(it -> it.getServerHost() + ":" +it.getServerPort()).collect(Collectors.toList()));
        runResultSB.append("<br>状态码：").append(runResult.getCode());
        runResultSB.append("<br>消息：").append(runResult.getMsg());

        runResult.setMsg(runResultSB.toString());
        return runResult;
    }

}
