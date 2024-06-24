package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitLog;
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
}
