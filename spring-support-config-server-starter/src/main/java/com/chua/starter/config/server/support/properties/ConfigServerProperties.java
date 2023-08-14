package com.chua.starter.config.server.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * 配置中心配置
 * @author CH
 * @since 2022/8/1 8:52
 */
@Data
@ConfigurationProperties(prefix = ConfigServerProperties.PRE, ignoreInvalidFields = true)
public class ConfigServerProperties {

    public static final String PRE = "plugin.configuration.server";
    public static final String DEFAULT_PROTOCOL = "http";
    /**
     * 启动协议
     */
    private String protocol = "http";
    /**
     * 是否开启
     */
    private boolean isOpen = true;
    /**
     * 是否开启密钥
     */
    private boolean openKey;
    /**
     * 是否开启客户端关闭自动删除配置项
     */
    private boolean openAutoDestroy;
    /**
     * configManager：database
     * 指定数据库
     */
    private String database;
    /**
     * 加密方式
     */
    private String encrypt = "aes";
    /**
     * 配置管理工厂(数据存放实现)
     * file
     * database
     */
    private String dataManager = "database";
    /**
     * 密钥管理工厂(鉴权位置)
     */
    private String keyManager = "database";

}
