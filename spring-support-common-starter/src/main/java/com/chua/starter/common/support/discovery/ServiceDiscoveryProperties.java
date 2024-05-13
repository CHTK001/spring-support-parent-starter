package com.chua.starter.common.support.discovery;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.common.support.discovery.ServiceDiscoveryProperties.PRE;
/**
 * 服务发现配置类，用于配置服务发现相关的参数。
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/5/13
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class ServiceDiscoveryProperties {

    // 配置前缀常量，用于指定配置文件中该配置的键的前缀
    public static final String PRE = "plugin.service.discovery";


    /**
     * 用于控制发现服务功能是否启用。
     * 默认值为 false，表示功能初始状态为禁用。
     */
    private boolean enable = false;

    /**
     * 用于标识发现服务的实现方式对象的类型或分类。
     * 其具体用途取决于上下文，在初始化时未指定具体值。
     */
    private String type = "redis";

    /**
     * 服务发现的URL地址
     */
    private String url;

    /**
     * 配置服务发现的用户名
     */
    private String username;

    /**
     * 配置服务发现的密码
     */
    private String password;
}
