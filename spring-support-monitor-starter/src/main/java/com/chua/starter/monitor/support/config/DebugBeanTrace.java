package com.chua.starter.monitor.support.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 临时调试钩子: 输出每个 Bean 的创建类名(AFTER 为代理后类名),
 * 第一个没有匹配 AFTER 的 BEFORE 行就是 CGLIB 轮旋的目标 Bean.
 * 已退出，仅保留 debug 日志回显
 * @author CH
 * @since 4.0.0.51
 */
@Component
public class DebugBeanTrace implements BeanPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DebugBeanTrace.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // 临时调试钩子：Bean 创建日志 debug 输出
        log.debug("BEAN.BEFORE {} / {}", beanName, bean.getClass().getName());
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 临时调试钩子：Bean 创建日志 debug 输出
        log.debug("BEAN.AFTER  {} / {}", beanName, bean.getClass().getName());
        return bean;
    }
}
