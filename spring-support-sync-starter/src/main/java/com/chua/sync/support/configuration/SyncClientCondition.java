package com.chua.sync.support.configuration;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 同步客户端条件判断
 * <p>
 * 当 type 为 client 或 both，且 client.enable = true 时，条件成立
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/10
 */
public class SyncClientCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 获取 type 配置，默认为 client
        String type = context.getEnvironment().getProperty("plugin.sync.type", "client");
        // 获取 client.enable 配置，默认为 true
        Boolean clientEnable = context.getEnvironment().getProperty("plugin.sync.client.enable", Boolean.class, false);
        
        // type 为 client 或 both 时，才检查 client.enable
        boolean typeMatch = "client".equalsIgnoreCase(type) || "both".equalsIgnoreCase(type);
        
        return typeMatch && clientEnable;
    }
}
