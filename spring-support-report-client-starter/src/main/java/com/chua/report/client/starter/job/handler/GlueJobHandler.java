package com.chua.report.client.starter.job.handler;

import com.chua.report.client.starter.job.log.DefaultJobLog;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
* Groovy脚本方式的作业处理程序
* <p>
* 通过Groovy动态编译执行脚本代码，支持在线编辑和热更新。
* 每次代码更新会生成新的版本号，确保执行的是最新代码。
* </p>
*
* @版本 1.0.0
* @since 2024/03/11
* @author CH
 */
@Slf4j
public class GlueJobHandler implements JobHandler {


    /**
    * 胶水代码更新时间戳
     */
    @Getter
    /** Glueupdatetime */
    private final long glueUpdatetime;

    /**
    * 实际执行的作业处理器
     */
    private final JobHandler jobHandler;

    /**
    * 构造函数
    *
    * @param jobHandler     实际执行的作业处理器实例
    * @param glueUpdatetime 胶水代码更新时间戳
     */
    public GlueJobHandler(JobHandler jobHandler, long glueUpdatetime) {
        this.jobHandler = jobHandler;
        this.glueUpdatetime = glueUpdatetime;
    }

    @Override
    /** 执行 */
    public void execute() throws Exception {
 // 记录胶水版本信息
        DefaultJobLog.log("----------- GLUE任务执行, 版本:" + glueUpdatetime + " -----------");
        log.debug("开始执行GLUE任务, 版本: {}", glueUpdatetime);
        jobHandler.execute();
        log.debug("GLUE任务执行完成, 版本: {}", glueUpdatetime);
    }

    @Override
    /** 初始化 */
    public void init() throws Exception {
        log.debug("初始化GLUE任务处理器, 版本: {}", glueUpdatetime);
        this.jobHandler.init();
    }

    @Override
    /** 销毁 */
    public void destroy() throws Exception {
        log.debug("销毁GLUE任务处理器, 版本: {}", glueUpdatetime);
        this.jobHandler.destroy();
    }
}
