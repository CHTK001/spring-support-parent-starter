package com.chua.payment.support.configuration;

import com.chua.common.support.reflection.ReflectUtils;
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
 * @author CH
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.chua.starter.oauth.client.support.properties.AuthClientProperties")
public class PaymentOauthWhitelistConfiguration {

    /** Payment_notify_whitelist_patterns */
    private static final List<String> PAYMENT_NOTIFY_WHITELIST_PATTERNS = List.of(
            "/api/notify/**"
    );

    @Bean
    /** PaymentOauthWhitelistInitializer */
    public SmartInitializingSingleton paymentOauthWhitelistInitializer(ApplicationContext applicationContext) {
        return () -> {
            try {
                Class<?> propertiesType = ReflectUtils.forName("com.chua.starter.oauth.client.support.properties.AuthClientProperties");
                Object authClientProperties = applicationContext.getBean(propertiesType);
                List<String> whitelist = (List<String>) ReflectUtils.invoke(authClientProperties, "getWhitelist", List.class);
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
                    ReflectUtils.invoke(authClientProperties, "setWhitelist", void.class, List.class, updatedWhitelist);
                }
            } catch (Exception e) {
                log.warn("[Payment][OAuth] 注入白名单失败: {}", e.getMessage());
            }
        };
    }
}

