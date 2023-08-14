package com.chua.starter.config.plugin;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.entity.KeyValue;
import com.chua.starter.config.properties.ConfigProperties;
import com.chua.starter.config.protocol.ProtocolProvider;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 脚本
 *
 * @author CH
 */
@Slf4j
@Spi(ConfigConstant.BEAN)
public class ScriptValuePlugin implements Plugin, BeanDefinitionRegistryPostProcessor, ApplicationContextAware {
    @Setter
    private ConfigProperties configProperties;
    @Setter
    private ProtocolProvider protocolProvider;


    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        List<ConfigProperties.Subscribe> subscribe = configProperties.getSubscribe();
        List<ConfigProperties.Subscribe> collect = subscribe.stream().filter(it -> ConfigConstant.BEAN.equals(it.getDataType())).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(collect)) {
            log.info("无订阅脚本");
            return;
        }

        ConfigProperties.Subscribe first = CollectionUtils.findFirst(collect);
        System.out.println();

    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void onListener(KeyValue keyValue) {
        System.out.println();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.configProperties = SpringBeanUtils.bindOrCreate(ConfigProperties.PRE, ConfigProperties.class);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(protocolProvider);
        if (protocolProvider instanceof ApplicationContextAware) {
            ((ApplicationContextAware) protocolProvider).setApplicationContext(applicationContext);
        }
    }
}
