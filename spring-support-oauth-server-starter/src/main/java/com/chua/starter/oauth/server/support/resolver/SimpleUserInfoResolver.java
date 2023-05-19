package com.chua.starter.oauth.server.support.resolver;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;

/**
 * 日志处理器
 *
 * @author CH
 */
public class SimpleUserInfoResolver implements UserInfoResolver, ApplicationContextAware {

    private Map<String, UserInfoResolver> userInfoResolvers;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.userInfoResolvers = applicationContext.getBeansOfType(UserInfoResolver.class);
    }
}
