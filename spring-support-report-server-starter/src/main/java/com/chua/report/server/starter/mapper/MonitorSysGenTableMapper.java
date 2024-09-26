package com.chua.report.server.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.report.server.starter.entity.MonitorSysGenTable;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys-gen表映射器
 *
 * @author CH
 * @since 2023/09/21
 */
@Mapper
public interface MonitorSysGenTableMapper extends BaseMapper<MonitorSysGenTable> {
}