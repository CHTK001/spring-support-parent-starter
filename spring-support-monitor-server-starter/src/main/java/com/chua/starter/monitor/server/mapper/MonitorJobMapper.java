package com.chua.starter.monitor.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.server.entity.MonitorJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MonitorJobMapper extends BaseMapper<MonitorJob> {
}