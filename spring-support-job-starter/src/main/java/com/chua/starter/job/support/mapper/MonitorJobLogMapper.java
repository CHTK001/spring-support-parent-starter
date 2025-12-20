package com.chua.starter.job.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.job.support.entity.MonitorJobLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务执行日志Mapper接口
 * <p>
 * 提供任务执行日志的数据库操作，继承自MyBatis-Plus的{@link BaseMapper}。
 * 支持日志的增删改查、按时间范围查询等操作。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see MonitorJobLog
 */
@Mapper
public interface MonitorJobLogMapper extends BaseMapper<MonitorJobLog> {

}
