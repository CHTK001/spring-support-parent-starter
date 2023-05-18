//package com.chua.starter.common.support.configuration;
//
//import com.chua.starter.core.support.eventbus.EventbusTemplate;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
//import org.springframework.boot.autoconfigure.AutoConfigureAfter;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
//import org.springframework.context.annotation.Bean;
//
//import java.util.concurrent.Executor;
//
///**
// * @author CH
// */
//public class EventBusConfiguration  {
//
//
//    @Bean
//    @ConditionalOnMissingBean
//    public EventbusTemplate EventbusTemplate(Executor executor) {
//        return EventbusTemplate.create(executor);
//    }
//
//    @AutoConfigureAfter({EventBusConfiguration.class, EventbusTemplate.class})
//    public static class EventBussProcessor implements SmartInstantiationAwareBeanPostProcessor{
//
//        private EventbusTemplate EventbusTemplate;
//        private static final String SPRING = "org.spring";
//
//        public EventBussProcessor(EventbusTemplate EventbusTemplate) {
//            this.EventbusTemplate = EventbusTemplate;
//        }
//
//        @Override
//        public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
//            String typeName = bean.getClass().getTypeName();
//            if(!typeName.startsWith(SPRING)) {
//                EventbusTemplate.register(bean);
//            }
//            return SmartInstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
//        }
//
//    }
//}
