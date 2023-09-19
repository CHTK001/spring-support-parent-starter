package com.chua.subscribe.support;

import com.chua.common.support.eventbus.Eventbus;
import com.chua.common.support.eventbus.SubscribeEventbus;
import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.subscribe.support.properties.EventbusProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.concurrent.Executor;

import static com.chua.common.support.eventbus.EventbusType.KAFKA;

/**
 * 订阅配置
 *
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties(EventbusProperties.class)
public class SubscribeConfiguration  {


    /**
     * 事件总线
     *
     * @param executor 遗嘱执行人
     * @return {@link Eventbus}
     */
    @Bean
    @ConditionalOnMissingBean
    public Eventbus eventbus(@Qualifier("default-executor-2") Executor executor) {
        return Eventbus.newDefault(executor);
    }
    @Bean
    @ConditionalOnMissingBean
    public EventBusProcessor eventBusProcessor(Eventbus eventbus, EventbusProperties properties) {
        return new EventBusProcessor(eventbus, properties);
    }

    public static class EventBusProcessor implements SmartInstantiationAwareBeanPostProcessor, InitializingAware {

        private static final String SPRING = "org.spring";
        private final Eventbus eventbus;
        private final EventbusProperties eventbusProperties;

        public EventBusProcessor(Eventbus eventbus, EventbusProperties eventbusProperties) {
            this.eventbus = eventbus;
            this.eventbusProperties = eventbusProperties;
            afterPropertiesSet();
        }

        @Override
        public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
            String typeName = bean.getClass().getTypeName();
            if(!typeName.startsWith(SPRING)) {
                eventbus.registerObject(bean);
            }
            return SmartInstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
        }

        @Override
        public void afterPropertiesSet() {
            Map<String, EventbusProperties.SubscribeEventbusConfig> subscribe = eventbusProperties.getSubscribe();
            for (Map.Entry<String, EventbusProperties.SubscribeEventbusConfig> entry : subscribe.entrySet()) {
                SubscribeEventbus subscribeEventbus = createSubscribeEventbus(entry.getKey(), entry.getValue());
                if(null == subscribeEventbus) {
                    log.warn("{}注册失败", entry.getKey());
                    continue;
                }
                eventbus.registerSubscriber(entry.getKey(), subscribeEventbus);
            }
        }

        /**
         * 创建订阅事件总线
         *
         * @param value 值
         * @param key   钥匙
         * @return {@link SubscribeEventbus}
         */
        private SubscribeEventbus createSubscribeEventbus(String key, EventbusProperties.SubscribeEventbusConfig value) {
            if(KAFKA.name().equalsIgnoreCase(key)) {
                return ServiceProvider.of(SubscribeEventbus.class).getNewExtension(key, value.getHost(), value.getPort(), value.getPasswd(), value.getUsername(), value.getGroupId());
            }
            return ServiceProvider.of(SubscribeEventbus.class).getNewExtension(key, value.getHost(), value.getPort(), value.getPasswd(), value.getUsername());
        }
    }
}
