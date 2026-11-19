package com.chua.report.client.starter.job.handler;

import com.chua.starter.job.support.handler.JobHandler;
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
 * @since 2024/03/11
 * @author CH
 */
@Slf4j
public class GlueJobHandler implements JobHandler {


    /**
     * 胶水代码更新时间戳
     */
    @Getter
    /**
     * Glueupdatetime
    */
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

    /**
     * 处理器唯一名称，沿用被包装处理器的名称。
     *
     * @return 名称
     */
    @Override
    public String name() {
        return jobHandler.name();
    }

    /**
     * 处理器描述。
     *
     * @return 描述
     */
    @Override
    public String desc() {
        return "GLUE(" + glueUpdatetime + ")";
    }

    @Override
    /**
     * 执行
     * @return 返回值
     * @throws Exception 异常
    */
    public void execute() throws Exception {
 // 记录胶水版本信息
        DefaultJobLog.log("----------- GLUE任务执行, 版本:" + glueUpdatetime + " -----------");
        log.debug("开始执行GLUE任务, 版本: {}", glueUpdatetime);
        jobHandler.execute();
        log.debug("GLUE任务执行完成, 版本: {}", glueUpdatetime);
    }

    @Override
    /**
     * 初始化
     * @return 返回值
     * @throws Exception 异常
    */
    public void init() throws Exception {
        log.debug("初始化GLUE任务处理器, 版本: {}", glueUpdatetime);
        this.jobHandler.init();
    }

    @Override
    /**
     * 销毁
     * @return 返回值
     * @throws Exception 异常
    */
    public void destroy() throws Exception {
        log.debug("销毁GLUE任务处理器, 版本: {}", glueUpdatetime);
        this.jobHandler.destroy();
    }
}
