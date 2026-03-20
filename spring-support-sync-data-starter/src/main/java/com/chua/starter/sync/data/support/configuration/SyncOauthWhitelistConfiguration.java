package com.chua.starter.sync.data.support.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 Sync 模块接口加入外层 OAuth 客户端白名单，避免和内置 Session 认证冲突。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.chua.starter.oauth.client.support.properties.AuthClientProperties")
public class SyncOauthWhitelistConfiguration {

    private static final List<String> SYNC_WHITELIST_PATTERNS = List.of(
            "/v1/sync/**",
            "/api/v1/sync/**",
            "/monitor/api/v1/sync/**"
    );

    @Bean
    public SmartInitializingSingleton syncOauthWhitelistInitializer(ApplicationContext applicationContext) {
        return () -> {
            try {
                Class<?> propertiesType =
                        Class.forName("com.chua.starter.oauth.client.support.properties.AuthClientProperties");
                Object authClientProperties = applicationContext.getBean(propertiesType);
                Method getWhitelist = propertiesType.getMethod("getWhitelist");
                Method setWhitelist = propertiesType.getMethod("setWhitelist", List.class);

                @SuppressWarnings("unchecked")
                List<String> whitelist = (List<String>) getWhitelist.invoke(authClientProperties);
                List<String> updatedWhitelist = whitelist == null ? new ArrayList<>() : new ArrayList<>(whitelist);

                boolean changed = false;
                for (String pattern : SYNC_WHITELIST_PATTERNS) {
                    if (!updatedWhitelist.contains(pattern)) {
                        updatedWhitelist.add(pattern);
                        changed = true;
                        log.info("[Sync][OAuth] 已将路径加入外层白名单: {}", pattern);
                    }
                }

                if (changed) {
                    setWhitelist.invoke(authClientProperties, updatedWhitelist);
                }
            } catch (Exception e) {
                log.warn("[Sync][OAuth] 注入外层白名单失败: {}", e.getMessage());
            }
        };
    }
}
