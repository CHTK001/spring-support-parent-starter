package com.chua.starter.job.support.handler;

import com.chua.starter.job.support.log.DefaultJobLog;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Groovy脚本方式的作业处理程序
 * <p>
 * 通过Groovy动态编译执行脚本代码，支持在线编辑和热更新。
 * 每次代码更新会生成新的版本号，确保执行的是最新代码。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Slf4j
public class GlueJobHandler implements JobHandler {

    /**
     * GLUE代码更新时间戳
     */
    @Getter
    private final long glueUpdatetime;

    /**
     * 实际执行的JobHandler
     */
    private final JobHandler jobHandler;

    /**
     * 构造函数
     *
     * @param jobHandler     实际执行的JobHandler实例
     * @param glueUpdatetime GLUE代码更新时间戳
     */
    public GlueJobHandler(JobHandler jobHandler, long glueUpdatetime) {
        this.jobHandler = jobHandler;
        this.glueUpdatetime = glueUpdatetime;
    }

    @Override
    public void execute() throws Exception {
        // 记录GLUE版本信息
        DefaultJobLog.log("----------- GLUE任务执行, 版本:" + glueUpdatetime + " -----------");
        log.debug("开始执行GLUE任务, 版本: {}", glueUpdatetime);
        jobHandler.execute();
        log.debug("GLUE任务执行完成, 版本: {}", glueUpdatetime);
    }

    @Override
    public void init() throws Exception {
        log.debug("初始化GLUE任务处理器, 版本: {}", glueUpdatetime);
        this.jobHandler.init();
    }

    @Override
    public void destroy() throws Exception {
        log.debug("销毁GLUE任务处理器, 版本: {}", glueUpdatetime);
        this.jobHandler.destroy();
    }
}
