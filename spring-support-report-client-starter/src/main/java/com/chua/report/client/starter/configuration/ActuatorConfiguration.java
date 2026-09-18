package com.chua.report.client.starter.configuration;

import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.context.annotation.Bean;

import java.util.Set;

/**
* Actuator配置
* @author CH
* @since 4.0.0
 */
public class ActuatorConfiguration {

    @Bean
    /**
    * 习俗sanitizer
    *
    * @return 习俗sanitizer的结果
    */
    public SanitizingFunction customSanitizer() {
        return data -> {
            // 自定义哪些属性需要脱敏
            String property = data.getKey();
            String key = data.getKey();

            // 默认的敏感关键词（可以扩展）
            Set<String> sensitivePatterns = Set.of(
                    "password", "secret", "key", "token",
                    "credential", "auth", "private", "cert"
            );

            // 检查是否需要脱敏
            boolean shouldSanitize = sensitivePatterns.stream()
                    .anyMatch(pattern ->
                            property.toLowerCase().contains(pattern) ||
                                    key.toLowerCase().contains(pattern)
                    );

            // 自定义哪些值不需要脱敏
            if (property.contains("public.key") ||
                    property.contains("allowed.origins")) {
                shouldSanitize = false;
            }

            /**
            * sanitizable数据。
            * @param data.getPropertySource( 数据.获取财产源(
            * @return sanitizable数据的结果
            */
            return shouldSanitize ? new SanitizableData(data.getPropertySource(), key, "******") : data;
        };
    }
}
