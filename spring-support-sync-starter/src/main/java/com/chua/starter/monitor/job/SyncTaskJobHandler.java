package com.chua.starter.sync.job;

import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.thread.JobContext;
import com.chua.starter.sync.service.sync.MonitorSyncTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 同步任务 JobHandler
 * <p>
 * 集成 spring-job 的任务处理器，支持通过定时任务触发同步任务执行。
 * 可以在 monitor_job 表中配置：
 * - job_execute_bean: syncTaskJobHandler
 * - job_execute_param: 同步任务ID（如: 1 或 1,2,3 多个任务）
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 * @version 1.0.0
 */
@Slf4j
@Component("syncTaskJobHandler")
public class SyncTaskJobHandler implements JobHandler {

    @Autowired
    private MonitorSyncTaskExecutor syncTaskExecutor;

    @Override
    public void execute() throws Exception {
        String param = JobContext.getJobParam();
        if (param == null || param.trim().isEmpty()) {
            log.warn("同步任务参数为空，请在 job_execute_param 中配置任务ID");
            return;
        }

        log.info("开始执行同步任务, 参数: {}", param);

        String[] taskIds = param.split(",");
        for (String taskIdStr : taskIds) {
            try {
                Long taskId = Long.parseLong(taskIdStr.trim());
                Long logId = syncTaskExecutor.executeOnce(taskId);
                log.info("同步任务执行完成, taskId: {}, logId: {}", taskId, logId);
            } catch (NumberFormatException e) {
                log.error("无效的任务ID: {}", taskIdStr);
            } catch (Exception e) {
                log.error("同步任务执行失败, taskId: {}", taskIdStr, e);
                throw e;
            }
        }
    }

    @Override
    public void init() throws Exception {
        log.debug("SyncTaskJobHandler 初始化");
    }

    @Override
    public void destroy() throws Exception {
        log.debug("SyncTaskJobHandler 销毁");
    }
}
