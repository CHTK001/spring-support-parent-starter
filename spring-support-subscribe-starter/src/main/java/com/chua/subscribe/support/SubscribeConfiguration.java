package com.chua.subscribe.support;

import com.chua.common.support.eventbus.EventRouter;
import com.chua.common.support.eventbus.Eventbus;
import com.chua.subscribe.support.properties.EventbusProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Map;


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
    public EventRouter eventRouter() {
        EventRouter eventRouter = Eventbus.newDefault();
        return eventRouter;
    }
    @Bean
    @ConditionalOnMissingBean
    public EventBusProcessor eventBusProcessor(EventRouter eventRouter, ApplicationContext applicationContext) {
        return new EventBusProcessor(eventRouter, applicationContext);
    }

    public static class EventBusProcessor implements SmartInstantiationAwareBeanPostProcessor {

        private static final String SPRING = "org.spring";
        private final EventRouter eventRouter;

        public EventBusProcessor(EventRouter eventRouter, ApplicationContext applicationContext) {
            this.eventRouter = eventRouter;
            Map<String, Eventbus> beansOfType = applicationContext.getBeansOfType(Eventbus.class);
            for (Map.Entry<String, Eventbus> entry : beansOfType.entrySet()) {
                eventRouter.addEventbus(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
            String typeName = bean.getClass().getTypeName();
            if(!typeName.startsWith(SPRING)) {
                eventRouter.registerObject(bean);
            }
            return SmartInstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
        }

    }
}
