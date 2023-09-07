package com.chua.starter.config.plugin;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.utils.CollectionUtils;
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
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 执行器插件
 *
 * @author CH
 * @since 2023/09/07
 */
@Setter
@Slf4j
@Spi(ConfigConstant.ACTUATOR)
public class ActuatorPlugin implements Plugin, BeanDefinitionRegistryPostProcessor {
    private Environment environment;

    @Setter
    private ProtocolProvider protocolProvider;

    private ApplicationContext applicationContext;
    @Setter
    private ConfigProperties configProperties;

    @Override
    public void onListener(KeyValue keyValue, Object response) {
        System.out.println();
    }


    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        List<ConfigProperties.Subscribe> subscribe = configProperties.getSubscribe();
        List<ConfigProperties.Subscribe> collect = subscribe.stream().filter(it -> ConfigConstant.BEAN.equals(it.getDataType())).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(collect)) {
            log.info("无订阅Actuator");
            return;
        }

        ConfigProperties.Subscribe first = CollectionUtils.findFirst(collect);
        protocolProvider.subscribe(first.getSubscribe(), first.getDataType(), new LinkedHashMap<>(), stringObjectMap -> {
        });
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }
}
