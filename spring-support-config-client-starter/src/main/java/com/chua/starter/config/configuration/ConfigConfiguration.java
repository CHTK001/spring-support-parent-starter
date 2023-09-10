package com.chua.starter.config.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ClassUtils;
import com.chua.starter.config.endpoint.OshiEndpoint;
import com.chua.starter.config.plugin.Plugin;
import com.chua.starter.config.properties.ConfigProperties;
import com.chua.starter.config.properties.ConfigUniformProperties;
import com.chua.starter.config.protocol.ProtocolProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 配置中心配置
 * @author CH
 * @since 2022/7/30 8:34
 */
@Import({OshiEndpoint.class, StartupTimeListener.class})
@ConditionalOnMissingClass(value = {"com.chua.starter.config.server.properties.ConfigServerProperties"})
@EnableConfigurationProperties({ConfigProperties.class, ConfigUniformProperties.class})
public class ConfigConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    @Resource
    private ConfigProperties configProperties;
    private ApplicationContext applicationContext;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if(!configProperties.isOpen()) {
            return;
        }

        if (ClassUtils.isPresent("com.chua.starter.config.server.support.configuration.ConfigServerConfiguration")) {
            return;
        }

        String protocol = configProperties.getProtocol();
        ProtocolProvider protocolProvider = ServiceProvider.of(ProtocolProvider.class).getExtension(protocol);
        if (null == protocolProvider) {
            return;
        }

        Map<String, Plugin> stringPluginMap = ServiceProvider.of(Plugin.class).list();
        for (Plugin plugin : stringPluginMap.values()) {
            registry.registerBeanDefinition(plugin.getClass().getTypeName() + "@" + protocol, BeanDefinitionBuilder
                    .genericBeanDefinition(plugin.getClass())
                    .addPropertyValue("protocolProvider", protocolProvider)
                    .addPropertyValue("applicationContext", applicationContext)
                    .getBeanDefinition());
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        configProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ConfigProperties.PRE, ConfigProperties.class);
    }
}
