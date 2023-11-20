package com.chua.starter.unified.client.support.properties;

import com.chua.common.support.net.NetUtils;
import com.chua.common.support.protocol.boot.ModuleType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;

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
    private String address = "http://127.0.0.1:31111/report";

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


    private Map<ModuleType, SubscribeOption> subscribe;

    /**
     * 获取订阅
     *
     * @param moduleType 模块类型
     * @return {@link SubscribeOption}
     */
    public SubscribeOption getSubscribeOption(ModuleType moduleType) {
        if(null == subscribe) {
            return null;
        }

        return subscribe.get(moduleType);
    }


    @Data
    public static class SubscribeOption {
        /**
         * 订阅列表
         */
        private List<String> subscribe;
        /**
         * 自动全部上报
         */
        private boolean autoConfig;
    }
    @Data
    public static class UnifiedExecuter {

        /**
         * 客户端绑定的IP
         */
        private String host = "127.0.0.1";
        /**
         * 客户端绑定的端口
         */
        private int port = 18765;

        public int getPort() {
            if(port < 1) {
                port = NetUtils.getAvailablePort();
            }
            return port;
        }

        public String getAddress() {
            return getHost() + ":" + getPort();
        }
    }
}
