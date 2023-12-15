package com.chua.starter.unified.client.support.factory;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.protocol.boot.BootOption;
import com.chua.common.support.protocol.boot.Protocol;
import com.chua.common.support.protocol.options.ServerOption;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ClassUtils;
import com.chua.starter.unified.client.support.mybatis.SupportInjector;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.env.Environment;

import static com.chua.common.support.discovery.Constants.SUBSCRIBE;

/**
 * 协议工厂
 *
 * @author CH
 */
public class ProtocolFactory implements InitializingAware {
    private final UnifiedClientProperties unifiedClientProperties;
    private final String appName;
    private final Environment environment;
    private final BeanDefinitionRegistry registry;
    private Protocol protocol1;

    public ProtocolFactory(UnifiedClientProperties unifiedClientProperties,
                           String appName,
                           Environment environment,
                           BeanDefinitionRegistry registry) {
        this.unifiedClientProperties = unifiedClientProperties;
        this.appName = appName;
        this.environment = environment;
        this.registry = registry;
    }


    public Protocol getProtocol() {
        return protocol1;
    }

    @Override
    public void afterPropertiesSet() {
        UnifiedClientProperties.UnifiedExecuter executer = unifiedClientProperties.getExecuter();
        String protocol = unifiedClientProperties.getProtocol();
        JSONObject jsonObject = new JSONObject();
        jsonObject.putAll(BeanMap.create(executer));
        jsonObject.put(SUBSCRIBE, unifiedClientProperties.getSubscribe());
        BootOption bootOption = BootOption.builder()
                .encryptionSchema(unifiedClientProperties.getEncryptionSchema())
                .encryptionKey(unifiedClientProperties.getEncryptionKey())
                .address(unifiedClientProperties.getAddress())
                .appName(appName)
                .ext(jsonObject)
                .profile(environment.getProperty("spring.profiles.active", "default"))
                .serverOption(ServerOption.builder().port(executer.getPort()).host(executer.getHost()).build())
                .build();
        this.protocol1 = ServiceProvider.of(Protocol.class).getNewExtension(protocol, bootOption);

        registry.registerBeanDefinition(protocol + "server", BeanDefinitionBuilder
                .rootBeanDefinition(protocol1.serverType())
                .addConstructorArgValue(bootOption)
                .setDestroyMethodName("close")
                .setInitMethodName("start")
                .getBeanDefinition()
        );
        registry.registerBeanDefinition(protocol + "client", BeanDefinitionBuilder
                .rootBeanDefinition(protocol1.clientType())
                .addConstructorArgValue(bootOption)
                .getBeanDefinition()
        );

        if(ClassUtils.isPresent("com.baomidou.mybatisplus.core.injector.DefaultSqlInjector")) {
            registry.registerBeanDefinition(SupportInjector.class.getTypeName(), BeanDefinitionBuilder
                    .rootBeanDefinition(SupportInjector.class)
                    .setPrimary(true)
                    .addConstructorArgReference(protocol + "client")
                    .addConstructorArgReference(protocol + "server")
                    .addConstructorArgValue(unifiedClientProperties)
                    .getBeanDefinition()
            );
        }
    }
}
