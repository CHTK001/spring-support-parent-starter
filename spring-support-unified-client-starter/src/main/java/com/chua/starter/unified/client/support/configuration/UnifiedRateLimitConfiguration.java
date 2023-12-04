package com.chua.starter.unified.client.support.configuration;

import com.chua.common.support.protocol.boot.ProtocolServer;
import com.chua.starter.unified.client.support.limit.RateLimitFactory;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.Resource;

/**
 * OSHI配置
 *
 * @author CH
 */
@Slf4j
public class UnifiedRateLimitConfiguration implements ApplicationContextAware {

    ProtocolServer protocolServer;

    @Resource
    private UnifiedClientProperties unifiedClientProperties;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        try {
            protocolServer = applicationContext.getBean(ProtocolServer.class);
            protocolServer.addMapping(RateLimitFactory.getInstance());
        } catch (BeansException ignored) {
        }
    }


}
