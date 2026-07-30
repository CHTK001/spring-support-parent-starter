package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.ServerHost;

/**
 * 服务器生命周期 SPI。
 *
 * <p>用于在主机创建/更新前按协议做差异化归一化和参数校验，
 * 让 LOCAL/SSH/WINRM/MAC 等类型的规则各自独立实现，避免业务层堆积 if-else。
 *
 * @author CH
 */
public interface ServerHostLifecycleSpi {

    /**
     * 在保存主机前执行协议侧归一化。
     *
     * @param host 服务器主机对象
     */
    void normalizeBeforeSave(ServerHost host);

    /**
     * 校验主机配置是否满足当前协议要求。
     *
     * @param host 服务器主机对象
     * @throws IllegalStateException 参数不满足协议约束时抛出
     */
    void validateForSave(ServerHost host);
}

