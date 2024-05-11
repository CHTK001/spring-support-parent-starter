package com.chua.starter.proxy.support.configuration;

import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.discovery.DiscoveryOption;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.proxy.support.properties.ProxyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * ProxyConfiguration 类用于配置代理设置。
 *
 * @author CH
 * @since 2024/5/11
 */
@Slf4j
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyConfiguration implements SmartInstantiationAwareBeanPostProcessor, ApplicationContextAware {


    private ServiceDiscovery serviceDiscovery;

    private String host;
    private Integer port;
    private Boolean isSsl;

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        register(beanClass);
        return SmartInstantiationAwareBeanPostProcessor.super.postProcessBeforeInstantiation(beanClass, beanName);
    }

    private void register(Class<?> beanClass) {
        if (serviceDiscovery == null) {
            return;
        }

        if(Proxy.isProxyClass(beanClass)) {
            return;
        }

        ReflectionUtils.doWithMethods(beanClass, method -> {
            if(!isMapping(method)) {
                return;
            }
            register(method);
        });
    }

    private void register(Method method) {
        RequestMapping requestMapping = method.getDeclaredAnnotation(RequestMapping.class);
        if(null != requestMapping) {
            register(requestMapping.value());
            return;
        }

        GetMapping getMapping = method.getDeclaredAnnotation(GetMapping.class);
        if(null != getMapping) {
            register(getMapping.value());
            return;
        }

        PostMapping postMapping = method.getDeclaredAnnotation(PostMapping.class);
        if(null != postMapping) {
            register(postMapping.value());
            return;
        }

        DeleteMapping deleteMapping = method.getDeclaredAnnotation(DeleteMapping.class);
        if(null != deleteMapping) {
            register(deleteMapping.value());
            return;
        }

        PutMapping putMapping = method.getDeclaredAnnotation(PutMapping.class);
        if(null != putMapping) {
            register(putMapping.value());
        }
    }

    private void register(String[] value) {
        for (String s : value) {
            if(StringUtils.isBlank(s)) {
                continue;
            }
            serviceDiscovery.registerService(s, Discovery.builder()
                            .host(host)
                            .port(port)
                            .protocol(isSsl ? "https" : "http")
                            .uriSpec(s)
                            .weight(1.0)
                    .build());
        }
    }

    private boolean isMapping(Method method) {
        return method.isAnnotationPresent(RequestMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)
                ;
    }


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

        this.port = applicationContext.getEnvironment().getRequiredProperty("server.port", Integer.class);
        this.isSsl = applicationContext.getEnvironment().getRequiredProperty("server.ssl.enabled", Boolean.class);
        this.host = StringUtils.defaultString(proxyProperties.getBindHost(), applicationContext.getEnvironment().getProperty("server.address"));
        this.serviceDiscovery = ServiceProvider.of(ServiceDiscovery.class)
                .getNewExtension(proxyProperties.getType(),
                        new DiscoveryOption()
                                .setPassword(proxyProperties.getPassword())
                                .setUser(proxyProperties.getUsername())
                                .setAddress(url));
    }
}
