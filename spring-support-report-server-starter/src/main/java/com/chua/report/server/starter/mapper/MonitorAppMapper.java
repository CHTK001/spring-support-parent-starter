package com.chua.report.server.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.report.server.starter.entity.MonitorApplication;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MonitorAppMapper extends BaseMapper<MonitorApplication> {
}