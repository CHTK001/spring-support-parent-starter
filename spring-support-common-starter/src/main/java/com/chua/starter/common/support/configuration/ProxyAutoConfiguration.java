package com.chua.starter.common.support.configuration;

import com.chua.common.support.context.resolver.ProxyResolver;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.properties.ProxyProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Arrays;
import java.util.List;

/**
 * autoconfiguration
 *
 * @author CH
 * @since 2022/7/28 9:03
 */
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyAutoConfiguration implements
        DisposableBean,
        ApplicationContextAware,
        BeanDefinitionRegistryPostProcessor {


    private ProxyProperties proxyProperties;

    @Override
    public void destroy() throws Exception {

    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!proxyProperties.isOpenProxy()) {
            return;
        }

        List<ProxyProperties.ProxyConfiguration> config = proxyProperties.getConfig();
        for (ProxyProperties.ProxyConfiguration proxyConfiguration : config) {
            if (proxyConfiguration.getType() == ProxyProperties.Type.HTTP) {
                continue;
            }

            ProxyResolver proxyResolver = ServiceProvider.of(ProxyResolver.class).getNewExtension(proxyConfiguration.getType().name(), new Object[]{null});
            if (null == proxyResolver) {
                continue;
            }

            registerProxyDefinition(registry, proxyResolver, proxyConfiguration);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    private void registerProxyDefinition(BeanDefinitionRegistry beanFactory,
                                         ProxyResolver proxyResolver,
                                         ProxyProperties.ProxyConfiguration proxyConfiguration) {

        String[] source = proxyConfiguration.getSource();
        String target = proxyConfiguration.getTarget();
        ProxyProperties.Type type = proxyConfiguration.getType();

        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(proxyResolver.getClass());
        beanDefinitionBuilder.addConstructorArgValue(proxyConfiguration);
        beanFactory.registerBeanDefinition(Arrays.toString(source) + target + type,
                beanDefinitionBuilder.getBeanDefinition());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.proxyProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ProxyProperties.PRE, ProxyProperties.class);
    }
}
