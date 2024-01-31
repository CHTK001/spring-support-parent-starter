package com.chua.starter.monitor.protocol;

import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.protocol.boot.BootOption;
import com.chua.common.support.protocol.boot.Protocol;
import com.chua.common.support.protocol.options.ServerOption;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.monitor.factory.MonitorFactory;
import com.chua.starter.monitor.properties.MonitorProperties;
import lombok.Getter;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * 协议工厂
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
public class ProtocolFactory implements InitializingAware {
    private final BeanDefinitionRegistry registry;
    private final MonitorFactory monitorFactory;
    private final MonitorProperties monitorProperties;
    @Getter
    private Protocol protocol;
    public ProtocolFactory(BeanDefinitionRegistry registry) {
        this.registry = registry;
        this.monitorFactory = MonitorFactory.getInstance();
        this.monitorProperties = this.monitorFactory.getMonitorProperties();
    }

    @Override
    public void afterPropertiesSet() {
        String protocol = monitorProperties.getProtocol();
        BootOption bootOption = BootOption.builder()
                .encryptionSchema(monitorProperties.getEncryptionSchema())
                .encryptionKey(monitorProperties.getEncryptionKey())
                .address(monitorProperties.getHost() + ":" + monitorProperties.getPort())
                .appName(monitorFactory.getAppName())
                .profile(monitorFactory.getActive())
                .serverOption(ServerOption.builder().port(monitorProperties.getPort()).host(monitorProperties.getHost()).build())
                .build();
        this.protocol = ServiceProvider.of(Protocol.class).getNewExtension(protocol, bootOption);

        registry.registerBeanDefinition(protocol + "server", BeanDefinitionBuilder
                .rootBeanDefinition(this.protocol.serverType())
                .addConstructorArgValue(bootOption)
                .setDestroyMethodName("close")
                .setInitMethodName("start")
                .getBeanDefinition()
        );
        registry.registerBeanDefinition(protocol + "client", BeanDefinitionBuilder
                .rootBeanDefinition(this.protocol.clientType())
                .addConstructorArgValue(bootOption)
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
