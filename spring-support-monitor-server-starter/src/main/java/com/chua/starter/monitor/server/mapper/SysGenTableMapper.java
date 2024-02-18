package com.chua.starter.monitor.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.server.entity.MonitorSysGenTable;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys-gen表映射器
 *
 * @author CH
 * @since 2023/09/21
 */
@Mapper
public interface SysGenTableMapper extends BaseMapper<MonitorSysGenTable> {
}