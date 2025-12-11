package com.chua.starter.pay.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/10/24
 */
@Data
@ConfigurationProperties(prefix = "plugin.pay")
public class PayProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /**
     * 状态机持久化类型
     */
    private StateMachinePersistType stateMachinePersistType = StateMachinePersistType.DATABASE;

    /**
     * Redis 缓存过期时间（秒），默认 30 分钟
     */
    private Long redisCacheExpireSeconds = 1800L;

    /**
     * 状态机超时时间（毫秒），默认 5 秒
     */
    private Long stateMachineTimeoutMillis = 5000L;

    /**
     * 是否启用状态机日志
     */
    private Boolean enableStateMachineLog = true;

    /**
     * 订单超时时间（分钟），默认 30 分钟
     */
    private Integer orderTimeoutMinutes = 30;

    /**
     * 状态机提供者
     * <p>
     * 通过 SPI 机制加载状态机实现，支持的提供者：
     * <ul>
     *   <li>spring: Spring StateMachine 实现（默认）</li>
     *   <li>common: utils-common 模块的轻量级状态机实现</li>
     * </ul>
     * </p>
     */
    private String stateMachineProvider = "spring";

    /**
     * 状态机持久化类型枚举
     */
    public enum StateMachinePersistType {
        /**
         * 仅数据库持久化
         */
        DATABASE("数据库持久化"),

        /**
         * 数据库 + Redis 缓存
         */
        DATABASE_REDIS("数据库+Redis缓存");

        private final String description;

        StateMachinePersistType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}

