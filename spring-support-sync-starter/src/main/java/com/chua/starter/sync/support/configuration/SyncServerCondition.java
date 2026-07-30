package com.chua.starter.sync.support.configuration;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 同步服务端条件判断
 * <p>
 * 当 type 为 server 或 both，且 server.enable = true 时，条件成立
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/10
 */
public class SyncServerCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 获取 type 配置，默认为 client
        String type = context.getEnvironment().getProperty("plugin.sync.type", "server");
        // 获取 server.enable 配置，默认为 false
        Boolean serverEnable = context.getEnvironment().getProperty("plugin.sync.server.enable", Boolean.class, false);
        
        // type 为 server 或 both 时，才检查 server.enable
        boolean typeMatch = "server".equalsIgnoreCase(type) || "both".equalsIgnoreCase(type);
        
        return typeMatch && serverEnable;
    }
}
