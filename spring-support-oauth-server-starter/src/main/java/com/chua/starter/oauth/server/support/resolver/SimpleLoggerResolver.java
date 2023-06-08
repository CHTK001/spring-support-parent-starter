package com.chua.starter.oauth.server.support.resolver;

import com.chua.starter.oauth.client.support.annotation.Extension;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import com.chua.starter.oauth.server.support.service.LoggerService;
import com.google.common.base.Strings;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志处理器
 *
 * @author CH
 */
public class SimpleLoggerResolver implements LoggerResolver, ApplicationContextAware {

    private Map<String, LoggerService> serviceMap;
    private static final Map<LoggerService, String> CACHE = new ConcurrentHashMap<>();
    private String log;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.serviceMap = applicationContext.getBeansOfType(LoggerService.class);
        AuthServerProperties authServerProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(AuthServerProperties.PRE, AuthServerProperties.class);
        this.log = authServerProperties.getLog();

    }

    @Override
    public void register(String module, String code, String message, String address) {
        for (LoggerService loggerService : serviceMap.values()) {
            if (isMatch(loggerService, log)) {
                try {
                    loggerService.register(module, code, message, address);
                } catch (Exception ignored) {
                }
            }
        }
    }


    /**
     * 是否匹配类型
     *
     * @param loggerService 服务
     * @param authType      类型
     * @return 是否匹配类型
     */
    private boolean isMatch(LoggerService loggerService, String authType) {
        String absent = CACHE.computeIfAbsent(loggerService, it -> {
            Class<? extends LoggerService> aClass = loggerService.getClass();
            Extension userLoginType = aClass.getDeclaredAnnotation(Extension.class);
            if (null == userLoginType) {
                return "";
            }
            return userLoginType.value();
        });

        return !Strings.isNullOrEmpty(absent) && absent.equals(authType);
    }
}
