package com.chua.starter.job.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.job.support.entity.MonitorJobLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务日志Mapper接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Mapper
public interface MonitorJobLogMapper extends BaseMapper<MonitorJobLog> {

}
