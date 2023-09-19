package com.chua.starter.rpc.support.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.rpc.support.properties.RpcProperties;
import com.chua.starter.rpc.support.resolver.BeanDefinitionResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;


/**
 * 远程配置
 *
 * @author CH
 */
@Slf4j
@ConditionalOnProperty(prefix = "plugin.rpc", name = "open", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(RpcProperties.class)
public class EnableRpcConfiguration implements ImportBeanDefinitionRegistrar, ApplicationContextAware {

    private RpcProperties rpcProperties;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        BeanDefinitionResolver beanDefinitionResolver = ServiceProvider.of(BeanDefinitionResolver.class).getNewExtension(rpcProperties.getImpl());
        beanDefinitionResolver.register(rpcProperties, registry);

    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.rpcProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(RpcProperties.PRE, RpcProperties.class);
    }
}
