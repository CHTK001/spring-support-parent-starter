package com.chua.payment.support.configuration;

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
 * 将支付异步通知接口加入 OAuth 客户端白名单
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.chua.starter.oauth.client.support.properties.AuthClientProperties")
public class PaymentOauthWhitelistConfiguration {

    private static final List<String> PAYMENT_NOTIFY_WHITELIST_PATTERNS = List.of(
            "/api/notify/**"
    );

    @Bean
    public SmartInitializingSingleton paymentOauthWhitelistInitializer(ApplicationContext applicationContext) {
        return () -> {
            try {
                Class<?> propertiesType = Class.forName("com.chua.starter.oauth.client.support.properties.AuthClientProperties");
                Object authClientProperties = applicationContext.getBean(propertiesType);
                Method getWhitelist = propertiesType.getMethod("getWhitelist");
                Method setWhitelist = propertiesType.getMethod("setWhitelist", List.class);

                @SuppressWarnings("unchecked")
                List<String> whitelist = (List<String>) getWhitelist.invoke(authClientProperties);
                List<String> updatedWhitelist = whitelist == null ? new ArrayList<>() : new ArrayList<>(whitelist);

                boolean changed = false;
                for (String pattern : PAYMENT_NOTIFY_WHITELIST_PATTERNS) {
                    if (!updatedWhitelist.contains(pattern)) {
                        updatedWhitelist.add(pattern);
                        changed = true;
                        log.info("[Payment][OAuth] 已将路径加入白名单: {}", pattern);
                    }
                }

                if (changed) {
                    setWhitelist.invoke(authClientProperties, updatedWhitelist);
                }
            } catch (Exception e) {
                log.warn("[Payment][OAuth] 注入白名单失败: {}", e.getMessage());
            }
        };
    }
}
