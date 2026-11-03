package com.chua.starter.monitor.support.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 临时调试钩子: 输出每个 Bean 的创建类名(AFTER 为代理后类名),
 * 第一个没有匹配 AFTER 的 BEFORE 行就是 CGLIB 轮旋的目标 Bean.
 * 已退出，仅保留控制台调试回显（P3C E1 豁免）
 */
@Component
public class DebugBeanTrace implements BeanPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // [P3C E1 豁免] 临时调试钩子：Bean 创建日志控制台回显
        System.out.println("BEAN.BEFORE " + beanName + " / " + bean.getClass().getName()); // [P3C E1 豁免]
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // [P3C E1 豁免] 临时调试钩子：Bean 创建日志控制台回显
        System.out.println("BEAN.AFTER  " + beanName + " / " + bean.getClass().getName()); // [P3C E1 豁免]
        return bean;
    }
}
