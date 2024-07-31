package com.chua.starter.monitor.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitLog;
import com.chua.starter.monitor.server.pojo.MonitorProxyLimitLogResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    /**
     * 根据条件查询代理限制日志并分页。
     * 该方法用于查询 MonitorProxyLimitLog 实体的分页信息，基于提供的查询条件和页码。
     * 主要用于日志统计和监控系统中，以便管理员能够分页查看代理限制的日志详情。
     *
     * @param page 分页对象，包含当前页码和每页记录数等信息，用于进行分页查询。
     *             分页对象由框架或工具类创建并传入，调用者需要设置页码和每页记录数等参数。
     * @param entity 查询条件实体，包含了各种可用于筛选日志的条件。
     *               可以通过设置实体的属性来指定查询的条件，如时间范围、代理IP等。
     * @return 返回一个 LogStatistic 对象，其中包含了查询结果的统计信息和分页后的日志列表。
     *         统计信息可能包括总记录数、总页数等，日志列表则为当前页码对应的日志记录集合。
     */
    List<MonitorProxyLimitLogResult> listForGeo(@Param("page") Page<MonitorProxyLimitLog> page, @Param("query") MonitorProxyLimitLog entity);
}