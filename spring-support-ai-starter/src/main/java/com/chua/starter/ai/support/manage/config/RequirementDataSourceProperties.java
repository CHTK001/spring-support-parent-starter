package com.chua.starter.ai.support.manage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 需求数据源提供方配置属性。
 *
 * <p>通过 {@code spring.ai.datasource.requirement.*} 前缀配置全局默认值。
 * 数据源级别的覆盖配置存储在 {@code sys_ai_data_source} 表的 配置 JSON 中。</p>
 *
 * <h4>示例配置：</h4>
 * <pre>
 * spring:
 * AI:
 * datasource:
 * requirement:
 * 默认-标签: lark
 * 同步-已启用: true
 * 同步-cron: "0 0 2 * * ?"    # 每天凌晨2点同步
 * 提供者:
 * lark:
 * 已启用: true
 * 超时-seconds: 30
 * generic:
 * 已启用: true
 * 超时-seconds: 60
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.datasource.requirement")
public class RequirementDataSourceProperties {

    /**
     * 默认标签
    */
    private String defaultTag = "default";

    /**
     * 同步开关
    */
    private boolean syncEnabled = false;

    /**
     * 同步 cron 表达式
    */
    private String syncCron = "0 0 2 * * ?";

    /**
     * 提供者配置映射
    */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /**
     * 提供者 级别配置覆盖。
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class ProviderConfig {
        /**
         * 是否启用
        */
        private boolean enabled = true;
        /**
         * 超时秒数
        */
        private int timeoutSeconds = 30;
        /**
         * 重试次数
        */
        private int retryCount = 1;
    }
}
