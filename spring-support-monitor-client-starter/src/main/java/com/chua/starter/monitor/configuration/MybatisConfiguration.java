package com.chua.starter.monitor.configuration;

import com.chua.common.support.protocol.boot.ProtocolClient;
import com.chua.common.support.protocol.boot.ProtocolServer;
import com.chua.starter.monitor.mybatis.SupportInjector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 配置文件插件
 *
 * @author CH
 */
@Slf4j
@AutoConfigureAfter({ProtocolClient.class, ProtocolServer.class})
public class MybatisConfiguration {


    @Bean
    @ConditionalOnMissingBean
    public SupportInjector supportInjector(ProtocolClient protocolClient, ProtocolServer protocolServer) {
        return new SupportInjector(protocolClient, protocolServer);
    }
}
