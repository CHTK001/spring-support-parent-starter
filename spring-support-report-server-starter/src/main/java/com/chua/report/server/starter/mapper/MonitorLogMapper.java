package com.chua.report.server.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.report.server.starter.entity.MonitorLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 监控日志
 * @author Administrator
 */
@Mapper
public interface MonitorLogMapper extends BaseMapper<MonitorLog> {
}