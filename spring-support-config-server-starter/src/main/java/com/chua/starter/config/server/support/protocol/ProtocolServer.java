package com.chua.starter.config.server.support.protocol;

import com.chua.common.support.function.NameAware;
import com.chua.starter.config.server.support.config.NotifyConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;

/**
 * 协议服务
 *
 * @author CH
 * @since 2022/8/1 8:58
 */
public interface ProtocolServer extends NameAware, InitializingBean, DisposableBean {
    /**
     * 通知
     *
     * @param configs   配置
     */
    void notifyClient(List<NotifyConfig> configs);

}
