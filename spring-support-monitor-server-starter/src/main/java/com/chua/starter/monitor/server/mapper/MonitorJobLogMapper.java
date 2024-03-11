package com.chua.starter.monitor.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.server.entity.MonitorJobLog;
import com.chua.starter.monitor.server.job.pojo.JobStatistic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MonitorJobLogMapper extends BaseMapper<MonitorJobLog> {
    /**
     * 时间
     *
     * @param entity 实体
     * @return {@link List}<{@link JobStatistic}>
     */
    List<JobStatistic> time(@Param("query") MonitorJobLog entity);
}