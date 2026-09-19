package com.chua.starter.sync.support.configuration;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 同步客户端条件判断
 * <p>
 * 当 类型 为 客户端 或 both，且 客户端.enable = true 时，条件成立
 * </p>
 *
 * @since 2024/12/10
 * @author CH
 */
public class SyncClientCondition implements Condition {

    @Override
    /** Matches */
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
 // 获取 类型 配置，默认为 客户端
        String type = context.getEnvironment().getProperty("plugin.sync.type", "client");
 // 获取 客户端.enable 配置，默认为 true
        Boolean clientEnable = context.getEnvironment().getProperty("plugin.sync.client.enable", Boolean.class, false);
        
 // 类型 为 客户端 或 both 时，才检查 客户端.enable
        boolean typeMatch = "client".equalsIgnoreCase(type) || "both".equalsIgnoreCase(type);
        
        return typeMatch && clientEnable;
    }
}
