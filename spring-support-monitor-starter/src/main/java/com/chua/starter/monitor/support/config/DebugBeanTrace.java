package com.chua.starter.monitor.support.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 临时调试钩子: 输出每个 Bean 的创建类名(AFTER 为代理后类名),
 * 第一个没有匹配 AFTER 的 BEFORE 行就是 CGLIB 轮旋的目标 Bean.
 */
@Component
public class DebugBeanTrace implements BeanPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        System.out.println("BEAN.BEFORE " + beanName + " / " + bean.getClass().getName());
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        System.out.println("BEAN.AFTER  " + beanName + " / " + bean.getClass().getName());
        return bean;
    }
}
