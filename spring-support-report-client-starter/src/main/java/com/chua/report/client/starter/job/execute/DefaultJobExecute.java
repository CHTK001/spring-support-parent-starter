package com.chua.report.client.starter.job.execute;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.request.ServletRequest;
import com.chua.common.support.protocol.request.ServletResponse;
import com.chua.report.client.starter.job.GlueTypeEnum;
import com.chua.report.client.starter.job.TriggerParam;
import com.chua.report.client.starter.job.glue.GlueFactory;
import com.chua.report.client.starter.job.handler.GlueJobHandler;
import com.chua.report.client.starter.job.handler.JobHandler;
import com.chua.report.client.starter.job.handler.JobHandlerFactory;
import com.chua.report.client.starter.job.handler.ScriptJobHandler;
import com.chua.report.client.starter.job.thread.JobThread;
import com.chua.report.client.starter.job.thread.JobThreadFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认作业执行实现
 * <p>
 * 根据任务类型（Bean、Groovy、Script）获取或创建对应的处理器和线程。
 * 支持处理器热更新和版本管理。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Slf4j
public class DefaultJobExecute implements JobExecute {


    @Override
    public ServletResponse run(ServletRequest request, TriggerParam triggerParam) {
        log.debug("开始执行任务, 任务ID: {}, 处理器: {}", triggerParam.getJobId(), triggerParam.getExecutorHandler());

        // 解析GLUE类型
        GlueTypeEnum glueTypeEnum = GlueTypeEnum.match(triggerParam.getGlueType());

        // 获取或创建任务处理器
        HandlerResult handler = getJobHandler(triggerParam, glueTypeEnum);
        if (handler == null || handler.getJobHandler() == null) {
            log.error("任务处理器未找到, 任务ID: {}, 处理器: {}", triggerParam.getJobId(), triggerParam.getExecutorHandler());
            return ServletResponse.error("任务处理器未找到: " + triggerParam.getExecutorHandler());
        }

        // 获取或创建任务线程
        JobThread jobThread = handler.getJobThread();
        if (jobThread == null) {
            jobThread = JobThreadFactory.registJobThread(triggerParam.getJobId(), handler.getJobHandler(), handler.getRemoveOldReason());
        }

        // 将触发参数推送到队列
        ReturnResult<String> pushResult = jobThread.pushTriggerQueue(triggerParam);
        if (pushResult.isOk()) {
            log.debug("任务触发成功, 任务ID: {}", triggerParam.getJobId());
            return ServletResponse.ok();
        } else {
            log.warn("任务触发失败, 任务ID: {}, 原因: {}", triggerParam.getJobId(), pushResult.getMsg());
            return ServletResponse.error(pushResult.getMsg());
        }
    }

    /**
     * 根据触发参数和GLUE类型获取或创建任务处理器
     *
     * @param triggerParam 触发参数
     * @param glueTypeEnum GLUE类型
     * @return 处理器结果
     */
    private HandlerResult getJobHandler(TriggerParam triggerParam, GlueTypeEnum glueTypeEnum) {
        // 加载已存在的任务线程
        JobThread jobThread = JobThreadFactory.loadJobThread(triggerParam.getJobId());
        JobHandler jobHandler = jobThread != null ? jobThread.getHandler() : null;
        String removeOldReason = null;
        HandlerResult result = new HandlerResult(jobHandler, jobThread, removeOldReason);

        // 处理Bean类型任务
        if (GlueTypeEnum.BEAN == glueTypeEnum) {
            JobHandler newJobHandler = JobHandlerFactory.getInstance().get(triggerParam.getExecutorHandler());

            // 检查是否需要替换处理器
            if (jobThread != null && jobHandler != newJobHandler) {
                removeOldReason = "处理器已更换，终止旧线程";
                jobThread = null;
                jobHandler = null;
            }

            if (jobHandler == null) {
                jobHandler = newJobHandler;
            }
            result.setJobHandler(jobHandler);
            return result;
        }


        // 处理Groovy类型任务
        if (GlueTypeEnum.GLUE_GROOVY == glueTypeEnum) {
            // 检查版本是否更新
            if (jobThread != null &&
                    !(jobThread.getHandler() instanceof GlueJobHandler
                            && ((GlueJobHandler) jobThread.getHandler()).getGlueUpdatetime() == triggerParam.getGlueUpdatetime())) {
                removeOldReason = "GLUE代码已更新，终止旧线程";
                jobThread = null;
                jobHandler = null;
            }

            if (jobHandler == null) {
                try {
                    JobHandler originJobHandler = GlueFactory.getInstance().loadNewInstance(triggerParam.getGlueSource());
                    GlueJobHandler glueJobHandler = new GlueJobHandler(originJobHandler, triggerParam.getGlueUpdatetime());
                    result.setJobHandler(glueJobHandler);
                    return result;
                } catch (Exception e) {
                    log.error("GLUE任务加载失败, 任务ID: {}", triggerParam.getJobId(), e);
                    return null;
                }
            }
        }


        // 处理脚本类型任务
        if (glueTypeEnum != null && glueTypeEnum.isScript()) {
            // 检查版本是否更新
            if (jobThread != null &&
                    !(jobThread.getHandler() instanceof ScriptJobHandler
                            && ((ScriptJobHandler) jobThread.getHandler()).getGlueUpdatetime() == triggerParam.getGlueUpdatetime())) {
                removeOldReason = "脚本代码已更新，终止旧线程";
                jobThread = null;
                jobHandler = null;
            }

            if (jobHandler == null) {
                ScriptJobHandler scriptJobHandler = new ScriptJobHandler(
                        triggerParam.getJobId(),
                        triggerParam.getGlueUpdatetime(),
                        triggerParam.getGlueSource(),
                        glueTypeEnum
                );
                result.setJobHandler(scriptJobHandler);
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
