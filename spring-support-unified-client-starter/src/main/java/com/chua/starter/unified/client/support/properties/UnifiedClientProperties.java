package com.chua.starter.unified.client.support.properties;

import com.chua.common.support.net.NetUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import static com.chua.starter.unified.client.support.properties.UnifiedClientProperties.PRE;

/**
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class UnifiedClientProperties {
    public static final String PRE = "plugin.unified";
    /**
     * 加密模式
     */
    private String encryptionSchema = "aes";

    /**
     * 加密密钥
     */
    private String encryptionKey = "123456";
    /**
     * 打开
     */
    private boolean open = false;

    /**
     * 统一服务端地址
     */
    private String address;

    /**
     * i18n
     */
    private String i18n = "CN";

    /**
     * 协议
     */
    private String protocol = "http";


    @NestedConfigurationProperty
    public UnifiedExecuter executer = new UnifiedExecuter();


    @Data
    public static class UnifiedExecuter {

        /**
         * 客户端绑定的IP
         */
        private String host = "127.0.0.1";
        /**
         * 客户端绑定的端口
         */
        private int port = -1;

        public int getPort() {
            if(port < 1) {
                port = NetUtils.getAvailablePort();
            }
            return port;
        }
    }
}
