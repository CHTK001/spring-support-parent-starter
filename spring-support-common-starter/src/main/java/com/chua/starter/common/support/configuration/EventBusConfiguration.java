package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.eventbus.EventbusTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;

/**
 * @author CH
 */
public class EventBusConfiguration  {



    @Bean
    @ConditionalOnMissingBean
    public EventbusTemplate EventbusTemplate(Executor executor) {
        return new EventbusTemplate(executor);
    }

    @AutoConfigureAfter({EventBusConfiguration.class, EventbusTemplate.class})
    public static class EventBussProcessor implements SmartInstantiationAwareBeanPostProcessor{

        private final EventbusTemplate eventbusTemplate;
        private static final String SPRING = "org.spring";

        public EventBussProcessor(EventbusTemplate eventbusTemplate) {
            this.eventbusTemplate = eventbusTemplate;
        }

        @Override
        public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
            String typeName = bean.getClass().getTypeName();
            if(!typeName.startsWith(SPRING)) {
                eventbusTemplate.register(bean);
            }
            return SmartInstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
        }

    }
}
