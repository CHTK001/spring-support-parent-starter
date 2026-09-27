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
 * 将支付异步通知接口加入 OAuth 免鉴权客户端白名单的配置类。
 *
 * <p>仅在 {@code AuthClientProperties} 存在时生效，通过反射读取并合并白名单，避免支付模块直接依赖 OAuth 客户端模块。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.chua.starter.oauth.client.support.properties.AuthClientProperties")
public class PaymentOauthWhitelistConfiguration {

    /**
     * 支付通知接口的免鉴权路径模式，通知回调不携带用户令牌。
     */
    private static final List<String> PAYMENT_NOTIFY_WHITELIST_PATTERNS = List.of(
            "/api/notify/**"
    );

    @Bean
    /**
     * 构建支付通知白名单初始化器。
     *
     * @param applicationContext 应用上下文
     * @return OAuth 白名单初始化器
     */
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
