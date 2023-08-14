package com.chua.starter.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedList;
import java.util.List;

import static com.chua.starter.config.properties.ConfigProperties.PRE;


/**
 * @author CH
 * @since 2022/7/30 8:37
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class ConfigProperties {

    public static final String PRE = "plugin.configuration";

    /**
     * 配置中心地址
     */
    private String configAddress = "127.0.0.1:18173/api/config";
    /**
     * 协议
     */
    private String protocol = "http";
    /**
     * 是否开启
     */
    private boolean isOpen = true;
    /**
     * 订阅的应用配置
     */
    private List<Subscribe> subscribe = new LinkedList<>();
    /**
     * 密钥
     */
    private String key;
    /**
     * 加密方式
     */
    private String encrypt = "aes";
    /**
     * 多网卡或者不确定网卡需要绑定该配置或者采用非http协议
     */
    private String bindIp = "127.0.0.1";
    /**
     * 多网卡或者不确定网卡需要绑定该配置或者采用非http协议
     */
    private int bindPort = 23987;
    /**
     * i18n
     */
    private String i18n = "zh_CN";
    /**
     * 重連次數
     */
    private Integer reconnectLimit = 3;
    /**
     * 是否自动刷新配置中心配置
     */
    private boolean autoRefresh;

    /**
     * 是否自动上报当前应用配置
     */
    private boolean openRegister;

    @Data
    public static class Subscribe {
        /**
         * 订阅的数据类型
         */
        private String dataType;
        /**
         * 订阅的名称(多个逗号分隔)
         */
        private String subscribe;
    }

}
