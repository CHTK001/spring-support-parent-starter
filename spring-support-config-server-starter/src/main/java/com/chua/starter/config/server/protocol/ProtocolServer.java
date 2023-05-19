package com.chua.starter.config.server.protocol;

import com.chua.common.support.function.NameAware;
import com.chua.starter.config.server.entity.NotifyConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * 协议服务
 *
 * @author CH
 * @since 2022/8/1 8:58
 */
public interface ProtocolServer extends ProtocolResolver, NameAware, InitializingBean, DisposableBean {
    /**
     * 通知
     *
     * @param config   配置
     * @param keyValue 數據
     */
    void notifyClient(NotifyConfig config, String keyValue);
}
