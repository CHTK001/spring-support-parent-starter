package com.chua.report.server.starter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.report.server.starter.entity.MonitorJobLog;
import com.chua.report.server.starter.job.pojo.JobStatisticResult;

public interface MonitorJobLogService extends IService<MonitorJobLog>{


    /**
     * 时间
     *
     * @param entity 实体
     * @return {@link MonitorJobLog}
     */
    JobStatisticResult time(MonitorJobLog entity);

    /**
     * 清除
     *
     * @param entity 实体
     * @return {@link JobStatisticResult}
     */
    Boolean clear(MonitorJobLog entity);
}
