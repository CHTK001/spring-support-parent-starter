package com.chua.report.server.starter.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.report.server.starter.entity.MonitorProxyLimitLog;
import com.chua.report.server.starter.pojo.LogStatistic;
import com.chua.report.server.starter.pojo.MonitorProxyLimitLogResult;

/**
 * MonitorProxyLimitLogService接口定义了对监控代理限制日志的操作。
 * 它继承自IService<MonitorProxyLimitLog>，表示对MonitorProxyLimitLog实体的CRUD操作。
 * @author CH
 * @since 2024/6/21
 */
public interface MonitorProxyLimitLogService extends IService<MonitorProxyLimitLog> {

    /**
     * 根据限制月份删除监控代理限制日志。
     * 此方法用于根据指定的限制月份删除相关的限制日志记录。
     * 删除操作可能是为了清理过期或不再需要的日志，或者是为了释放存储空间。
     *
     * @param limitMonth 限制月份，作为删除操作的条件。
     * @return 返回一个布尔值，表示删除操作是否成功。
     *         如果删除成功，返回true；如果删除失败，返回false。
     */
    boolean delete(Integer limitMonth);

    /**
     * 分页查询监控代理限制日志。
     * 此方法用于根据指定的分页条件（page和entity）查询监控代理限制日志。
     * 分页查询通常用于在列表页面上显示分页数据，以便用户可以按需查看数据。
     *
     * @param page    分页条件，包括页码和每页记录数。
     * @param entity  查询条件，用于过滤结果。
     * @return 返回一个Page对象，表示分页查询结果。
     *         Page对象包含了分页数据，包括记录列表和分页信息。
     */
    Page<MonitorProxyLimitLogResult> pageForLog(Page<MonitorProxyLimitLog> page, MonitorProxyLimitLog entity);

    /**
     * 统计地理信息。
     * 此方法用于统计指定条件的地理信息，并返回统计结果。
     * 统计地理信息通常用于在统计页面上显示地理信息统计数据，以便用户可以了解数据分布情况。
     *
     * @param page    分页条件，包括页码和每页记录数。
     * @param entity  查询条件，用于过滤结果。
     * @return 返回一个LogStatistic对象，表示统计结果。
     *         LogStatistic对象包含了统计数据，包括记录列表和统计信息。
     */
    LogStatistic listForGeo(Page<MonitorProxyLimitLog> page, MonitorProxyLimitLog entity);
}
