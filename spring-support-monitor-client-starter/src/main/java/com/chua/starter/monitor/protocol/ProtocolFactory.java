package com.chua.starter.monitor.protocol;

import com.chua.common.support.collection.Option;
import com.chua.common.support.collection.Options;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.net.NetAddress;
import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.protocol.Protocol;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.monitor.factory.MonitorFactory;
import com.chua.starter.monitor.properties.MonitorProperties;
import com.chua.starter.monitor.properties.MonitorProtocolProperties;
import lombok.Getter;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.env.Environment;

/**
 * 协议工厂
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
public class ProtocolFactory implements InitializingAware {
    private final BeanDefinitionRegistry registry;
    private final Environment environment;
    private final MonitorFactory monitorFactory;
    private final MonitorProperties monitorProperties;
    private final MonitorProtocolProperties monitorProtocolProperties;
    @Getter
    private Protocol protocol;
    public ProtocolFactory(BeanDefinitionRegistry registry, Environment environment) {
        this.registry = registry;
        this.environment = environment;
        this.monitorFactory = MonitorFactory.getInstance();
        this.monitorProperties = this.monitorFactory.getMonitorProperties();
        this.monitorProtocolProperties = this.monitorFactory.getMonitorProtocolProperties();
    }

    @Override
    public void afterPropertiesSet() {
        String protocol = monitorProtocolProperties.getProtocol();
        Codec codec = null;
        try {
            codec = Codec.build(monitorProtocolProperties.getEncryptionSchema(), monitorProtocolProperties.getEncryptionKey());
        } catch (Exception ignored) {
        }
        NetAddress netAddress = NetAddress.of(environment.resolvePlaceholders(monitorProperties.getMonitor()));
        ProtocolSetting protocolSetting = ProtocolSetting.builder()
                .codec(codec)
                .host(monitorProtocolProperties.getHost())
                .port(monitorProtocolProperties.getPort())
                .protocol(protocol)
                .host(netAddress.getHost())
                .port(netAddress.getPort())
                .options(new Options()
                        .addOption("appName", new Option(monitorFactory.getAppName()))
                        .addOption("profile", new Option(monitorFactory.getActive()))
                )
                .heartbeat(false)
                .build();
        this.protocol = ServiceProvider.of(Protocol.class).getNewExtension(protocol, protocolSetting);

        registry.registerBeanDefinition(protocol + "server", BeanDefinitionBuilder
                .rootBeanDefinition(this.protocol.getServerType())
                .addConstructorArgValue(protocolSetting)
                .setDestroyMethodName("close")
                .setInitMethodName("start")
                .getBeanDefinition()
        );
        registry.registerBeanDefinition(protocol + "client", BeanDefinitionBuilder
                .rootBeanDefinition(this.protocol.getClientType())
                .addConstructorArgValue(protocolSetting)
                .getBeanDefinition()
        );

//        if(ClassUtils.isPresent("com.baomidou.mybatisplus.core.injector.DefaultSqlInjector")) {
//            registry.registerBeanDefinition(SupportInjector.class.getTypeName(), BeanDefinitionBuilder
//                    .rootBeanDefinition(SupportInjector.class)
//                    .setPrimary(true)
//                    .addConstructorArgReference(protocol + "client")
//                    .addConstructorArgReference(protocol + "server")
//                    .addConstructorArgValue(monitorProperties)
//                    .getBeanDefinition()
//            );
//        }
    }
}
