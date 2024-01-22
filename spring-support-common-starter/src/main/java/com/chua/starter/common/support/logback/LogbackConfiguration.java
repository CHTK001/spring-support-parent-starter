package com.chua.starter.common.support.logback;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * logback配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
public class LogbackConfiguration implements ApplicationContextAware {
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    }
}
