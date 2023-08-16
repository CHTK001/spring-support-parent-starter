package com.chua.starter.config.server.support.properties;

import com.chua.common.support.lang.store.StoreConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * 同一配置中心配置
 * @author CH
 * @since 2022/8/1 8:52
 */
@Data
@ConfigurationProperties(prefix = ConfigUniformProperties.PRE, ignoreInvalidFields = true)
public class ConfigUniformProperties {

    public static final String PRE = "plugin.configuration.server.uniform";
    public static final String DEFAULT_PROTOCOL = "mq";
    /**
     * 启动协议
     */
    private String protocol = "mq";
    /**
     * 是否开启
     */
    private boolean isOpen = true;
    /**
     * 访问主机
     */
    private String host = "127.0.0.1";
    /**
     * 访问主机
     */
    private int port = 23579;

    /**
     * 端点
     */
    private String endpoint = "uniform";
    /**
     * 文件存储位置
     */
    private String store = "./";

    /**
     * 存储配置
     */
    private StoreConfig storeConfig = new StoreConfig();

}
