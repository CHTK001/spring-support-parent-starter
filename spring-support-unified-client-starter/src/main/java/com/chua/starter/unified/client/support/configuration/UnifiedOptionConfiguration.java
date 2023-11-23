package com.chua.starter.unified.client.support.configuration;

import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.ProtocolServer;
import com.chua.common.support.protocol.server.annotations.ServiceMapping;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * OSHI配置
 *
 * @author CH
 */
public class UnifiedOptionConfiguration implements ApplicationContextAware {

    ProtocolServer protocolServer;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            protocolServer = applicationContext.getBean(ProtocolServer.class);
            protocolServer.addMapping(this);
        } catch (BeansException ignored) {
        }
    }


    /**
     * 获取当前服务端配置
     *
     * @param request 请求
     * @return {@link BootResponse}
     */
    @ServiceMapping("oshi")
    public BootResponse oshi(BootRequest request ) {
        return BootResponse.ok();
    }

    /**
     * 补丁
     *
     * @param request 请求
     * @return {@link BootResponse}
     */
    @ServiceMapping("patch")
    public BootResponse patch(BootRequest request ) {
        return BootResponse.ok();
    }

}
