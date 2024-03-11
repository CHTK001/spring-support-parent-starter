package com.chua.starter.monitor.job.handler;

import com.chua.starter.monitor.job.log.JobLog;

/**
 * 胶粘作业处理程序
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public class GlueJobHandler implements JobHandler{

    private long glueUpdatetime;
    private JobHandler jobHandler;
    public GlueJobHandler(JobHandler jobHandler, long glueUpdatetime) {
        this.jobHandler = jobHandler;
        this.glueUpdatetime = glueUpdatetime;
    }
    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    @Override
    public void execute() throws Exception {
        JobLog.getDefault().info("----------- glue.version:"+ glueUpdatetime +" -----------");
        jobHandler.execute();
    }

    @Override
    public void init() throws Exception {
        this.jobHandler.init();
    }

    @Override
    public void destroy() throws Exception {
        this.jobHandler.destroy();
    }
}
