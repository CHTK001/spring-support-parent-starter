package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
/**
 * Actuator属性类。
 *
 * 该类用于绑定配置文件中以"plugin.actuator"为前缀的属性，提供对这些属性的访问和管理。
 * 通过此类，可以方便地获取和设置与Actuator插件相关的配置信息，以支持插件的功能和行为定制。
 *
 * @author CH
 * @since 2024/6/21
 */
@Data
@ConfigurationProperties(prefix = "plugin.actuator")
public class ActuatorProperties {

    /**
     * 指定Actuator插件是否启用。
     *
     * 通过此属性可以动态启用或禁用Actuator插件。当设置为"true"时，插件生效；当设置为"false"时，插件被禁用。
     */
    private boolean enable;

    /**
     * Actuator插件的用户名。
     *
     * 此属性用于配置Actuator插件的访问控制，当启用用户名和密码认证时，需要提供正确的用户名。
     */
    private String username = "actuator";

    /**
     * Actuator插件的密码。
     *
     * 此属性用于配置Actuator插件的访问控制，当启用用户名和密码认证时，需要提供正确的密码。
     */
    private String password = "actuator";
}
