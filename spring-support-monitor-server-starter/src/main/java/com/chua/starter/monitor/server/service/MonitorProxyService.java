package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.server.entity.MonitorProxy;

/**
 * @author CH
 * @since 2024/5/13
 */
public interface MonitorProxyService extends IService<MonitorProxy> {


    /**
     * 启动监控代理。
     * <p>此方法用于启动传入的监控代理实例。如果启动成功，返回true；否则返回false。</p>
     *
     * @param monitorProxy 监控代理实例，不能为空。
     * @return boolean 返回启动结果，成功为true，失败为false。
     */
    ReturnResult<Boolean> start(MonitorProxy monitorProxy);

    /**
     * 停止监控代理。
     * <p>此方法用于停止传入的监控代理实例。如果停止成功，返回true；否则返回false。</p>
     *
     * @param monitorProxy 监控代理实例，不能为空。
     * @return boolean 返回停止结果，成功为true，失败为false。
     */
    ReturnResult<Boolean> stop(MonitorProxy monitorProxy);
}
