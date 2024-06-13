package com.chua.starter.proxy.support.configuration;

import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.discovery.DiscoveryOption;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.proxy.support.properties.ProxyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * ProxyConfiguration 类用于配置代理设置。
 *
 * @author CH
 * @since 2024/5/11
 */
@Slf4j
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyConfiguration implements ApplicationContextAware {


    private ServiceDiscovery serviceDiscovery;


    @ConditionalOnMissingBean
    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ProxyProperties proxyProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ProxyProperties.PRE, ProxyProperties.class);
        String url = proxyProperties.getUrl();
        if(StringUtils.isBlank(url)) {
            return;
        }


        int weight = proxyProperties.getWeight();
        Integer port = applicationContext.getEnvironment().getRequiredProperty("server.port", Integer.class);
        Boolean isSsl = applicationContext.getEnvironment().getRequiredProperty("server.ssl.enabled", Boolean.class);
        String host = StringUtils.defaultString(proxyProperties.getBindHost(), applicationContext.getEnvironment().getProperty("server.address"));
        String contextPath = applicationContext.getEnvironment().getProperty("server.servlet.context-path");
        ThreadUtils.newThread(() -> {
            this.serviceDiscovery = ServiceProvider.of(ServiceDiscovery.class)
                    .getNewExtension(proxyProperties.getType(),
                            new DiscoveryOption()
                                    .setPassword(proxyProperties.getPassword())
                                    .setUser(proxyProperties.getUsername())
                                    .setAddress(url));
            this.serviceDiscovery.registerService(StringUtils.endWithAppend(contextPath, "/"), Discovery.builder()
                    .host(host)
                    .port(port)
                    .timeout(proxyProperties.getTimeoutMills())
                    .weight(weight).build());
        }).start();
    }
}
