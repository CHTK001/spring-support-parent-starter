package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.server.entity.MonitorProxy;
import com.chua.starter.monitor.server.entity.MonitorProxyConfig;

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

    /**
     * 刷新监控代理。
     * <p>此方法用于刷新传入的监控代理实例。</p>
     *
     * @param proxyId 监控代理实例的ID，不能为空。
     */
    void refresh(String proxyId);

    /**
     * 尝试升级指定代理实例。
     * <p>
     * 此方法旨在检查并执行指定代理实例的升级操作。升级过程可能涉及检查当前版本、下载新版本、安装新版本以及必要的配置更新。
     * <p>
     * 参数:
     * proxyId - 代理实例的唯一标识符，用于定位和识别需要升级的代理实例。
     * <p>
     * 返回值:
     * 如果升级成功，则返回true；如果升级失败或无法执行升级，则返回false。
     * <p>
     * 注意:
     * - 方法的具体实现应确保操作的原子性，即在升级过程中，如果出现任何错误，应确保系统处于可恢复状态。
     * - 方法应记录详细的日志信息，以便于问题排查和分析。
     */
    default Boolean upgrade(String proxyId) {
        refresh(proxyId);
        return true;
    }

    /**
     * 更新代理配置信息。
     *
     * 此方法旨在更新数据库中的特定代理配置。它接收一个代理配置对象作为参数，
     * 并尝试在数据库中找到对应的配置进行更新。更新成功则返回true，表示配置已更新；
     * 如果更新失败，例如由于找不到对应的配置记录，則返回false。
     *
     * @param one 一个MonitorProxyConfig对象，包含待更新的代理配置信息。
     * @return 如果配置更新成功，则返回true；否则返回false。
     */
    Boolean updateConfigForProxy(MonitorProxyConfig one);
}
