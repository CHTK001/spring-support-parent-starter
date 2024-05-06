package com.chua.starter.monitor.configuration;

import com.chua.common.support.protocol.boot.BootProtocolClient;
import com.chua.common.support.protocol.boot.BootProtocolServer;
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
@AutoConfigureAfter({BootProtocolClient.class, BootProtocolServer.class})
public class MybatisConfiguration  {


    @Bean
    @ConditionalOnMissingBean
    public SupportInjector supportInjector() {
        return new SupportInjector();
    }
}
