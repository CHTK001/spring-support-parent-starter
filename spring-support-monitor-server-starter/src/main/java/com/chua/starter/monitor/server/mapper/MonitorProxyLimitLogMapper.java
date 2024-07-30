package com.chua.starter.monitor.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitLog;
import com.chua.starter.monitor.server.pojo.MonitorProxyLimitLogResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author CH
 * @since 2024/7/30
 */
@Mapper
public interface MonitorProxyLimitLogMapper extends BaseMapper<MonitorProxyLimitLog> {
    /**
     * 分页查询监控代理限制日志。
     * 该方法用于根据提供的分页信息和查询条件，从数据库中分页查询监控代理限制日志。
     *
     * @param page   分页信息，包含当前页码和每页条数等信息。
     * @param entity 查询条件实体，实体中的属性用于构建查询条件。
     * @return 返回分页后的监控代理限制日志列表。
     */
    Page<MonitorProxyLimitLogResult> pageForLog(@Param("page") Page<MonitorProxyLimitLog> page, @Param("query") MonitorProxyLimitLog entity);
}