package com.chua.starter.sync.support.configuration;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 同步服务端条件判断
 * <p>
 * 当 类型 为 服务端 或 both，且 服务端.enable = true 时，条件成立
 * </p>
 *
 * @since 2024/12/10
 * @author CH
 */
public class SyncServerCondition implements Condition {

    @Override
    /**
     * Matches
    */
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
 // 获取 类型 配置，默认为 客户端
        String type = context.getEnvironment().getProperty("plugin.sync.type", "server");
 // 获取 服务端.enable 配置，默认为 false
        Boolean serverEnable = context.getEnvironment().getProperty("plugin.sync.server.enable", Boolean.class, false);
        
 // 类型 为 服务端 或 both 时，才检查 服务端.enable
        boolean typeMatch = "server".equalsIgnoreCase(type) || "both".equalsIgnoreCase(type);
        
        return typeMatch && serverEnable;
    }
}
