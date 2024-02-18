package com.chua.starter.monitor.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.server.entity.MonitorSysGenColumn;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys-gen列映射器
 *
 * @author CH
 * @since 2023/09/21
 */
@Mapper
public interface SysGenColumnMapper extends BaseMapper<MonitorSysGenColumn> {
}