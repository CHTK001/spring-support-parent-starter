package com.chua.starter.common.support.actuator;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Actuator配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/6/21
 */
@Data
@ConfigurationProperties(prefix = "plugin.actuator", ignoreInvalidFields = true)
public class ActuatorProperties {

    /**
     * 是否开启认证
     */
    private boolean enableAuth = false;

    /**
     * 用户名
     */
    private String username = "admin";

    /**
     * 密码
     */
    private String password = "admin";

    /**
     * IP白名单
     */
    private Set<String> ipWhitelist = new LinkedHashSet<>();

    /**
     * 排除的路径
     */
    private Set<String> excludePaths = new LinkedHashSet<>();
}
