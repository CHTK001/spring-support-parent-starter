package com.chua.starter.monitor.job.execute;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.request.DefaultResponse;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.starter.monitor.factory.MonitorFactory;
import com.chua.starter.monitor.job.GlueTypeEnum;
import com.chua.starter.monitor.job.TriggerParam;
import com.chua.starter.monitor.job.glue.GlueFactory;
import com.chua.starter.monitor.job.handler.GlueJobHandler;
import com.chua.starter.monitor.job.handler.JobHandler;
import com.chua.starter.monitor.job.handler.JobHandlerFactory;
import com.chua.starter.monitor.job.handler.ScriptJobHandler;
import com.chua.starter.monitor.job.thread.JobThread;
import com.chua.starter.monitor.job.thread.JobThreadFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作业执行
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public class DefaultJobExecute implements JobExecute {


    @Override
    public Response run(Request request, TriggerParam triggerParam) {

        boolean inProfile = MonitorFactory.getInstance().inProfile(triggerParam.getProfile());

        // valid：jobHandler + jobThread
        GlueTypeEnum glueTypeEnum = GlueTypeEnum.match(triggerParam.getGlueType());

        HandlerResult handler = getJobHandler(triggerParam, glueTypeEnum);
        if (!inProfile || null == handler  || handler.getJobHandler() == null ) {
            return Response.notSupport(request, "job handler [" + triggerParam.getExecutorHandler() + "] not found.");
        }
        JobThread jobThread = handler.getJobThread();
        // replace thread (new or exists invalid)
        if (jobThread == null) {
            jobThread = JobThreadFactory.registJobThread(triggerParam.getJobId(), handler.getJobHandler(), handler.getRemoveOldReason());
        }

        // push data to queue
        ReturnResult<String> pushResult = jobThread.pushTriggerQueue(triggerParam);
        return new DefaultResponse(request, pushResult.isOk() ? 200 : 400, pushResult.getMsg());
    }

    private HandlerResult getJobHandler(TriggerParam triggerParam, GlueTypeEnum glueTypeEnum) {
        JobThread jobThread = JobThreadFactory.loadJobThread(triggerParam.getJobId());
        JobHandler jobHandler = jobThread != null ? jobThread.getHandler() : null;
        String removeOldReason = null;
        HandlerResult result = new HandlerResult(jobHandler, jobThread, removeOldReason);

        if (GlueTypeEnum.BEAN == glueTypeEnum) {

            // new jobhandler
            JobHandler newJobHandler = JobHandlerFactory.getInstance().get(triggerParam.getExecutorHandler());

            // valid old jobThread
            if (jobThread!=null && jobHandler != newJobHandler) {
                // change handler, need kill old thread
                removeOldReason = "change jobhandler or glue type, and terminate the old job thread.";

                jobThread = null;
                jobHandler = null;
            }

            // valid handler
            if (jobHandler == null) {
                jobHandler = newJobHandler;
            }
            result.setJobHandler(jobHandler);
            return result;
        }


        if (GlueTypeEnum.GLUE_GROOVY == glueTypeEnum) {

            // valid old jobThread
            if (jobThread != null &&
                    !(jobThread.getHandler() instanceof GlueJobHandler
                            && ((GlueJobHandler) jobThread.getHandler()).getGlueUpdatetime()==triggerParam.getGlueUpdatetime() )) {
                // change handler or gluesource updated, need kill old thread
                removeOldReason = "change job source or glue type, and terminate the old job thread.";

                jobThread = null;
                jobHandler = null;
            }

            // valid handler
            if (jobHandler == null) {
                try {
                    JobHandler originJobHandler = GlueFactory.getInstance().loadNewInstance(triggerParam.getGlueSource());
                    GlueJobHandler glueJobHandler = new GlueJobHandler(originJobHandler, triggerParam.getGlueUpdatetime());
                    result.setJobHandler(glueJobHandler);
                    return result;
                } catch (Exception e) {
                    return null;
                }
            }
        }


        if (glueTypeEnum!=null && glueTypeEnum.isScript()) {

            // valid old jobThread
            if (jobThread != null &&
                    !(jobThread.getHandler() instanceof ScriptJobHandler
                            && ((ScriptJobHandler) jobThread.getHandler()).getGlueUpdatetime()==triggerParam.getGlueUpdatetime() )) {
                // change script or gluesource updated, need kill old thread
                removeOldReason = "change job source or glue type, and terminate the old job thread.";

                jobThread = null;
                jobHandler = null;
            }

            // valid handler
            if (jobHandler == null) {
                ScriptJobHandler scriptJobHandler = new ScriptJobHandler(triggerParam.getJobId(), triggerParam.getGlueUpdatetime(), triggerParam.getGlueSource(), GlueTypeEnum.match(triggerParam.getGlueType()));
                result.setJobHandler(jobHandler);
            }
        }


        return result;
    }
}
@Data
@NoArgsConstructor
@AllArgsConstructor
class HandlerResult {
    private JobHandler jobHandler;
    private JobThread jobThread;
    private String removeOldReason;
}
