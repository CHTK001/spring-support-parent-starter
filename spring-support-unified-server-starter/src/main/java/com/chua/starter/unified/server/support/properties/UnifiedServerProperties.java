package com.chua.starter.unified.server.support.properties;

import com.chua.common.support.protocol.boot.encryption.BootRequestDecode;
import com.chua.common.support.protocol.boot.encryption.BootRequestEncode;
import com.chua.common.support.protocol.boot.encryption.BootResponseDecode;
import com.chua.common.support.spi.ServiceProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.unified.server.support.properties.UnifiedServerProperties.PRE;

/**
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class UnifiedServerProperties {

    public static final String PRE = "plugin.unified.server";

    /**
     * 加密模式
     */
    private String encryptionSchema = "aes";

    /**
     * 加密密钥
     */
    private String encryptionKey = "123456";

    /**
     * 保持活动超时(s)
     */
    private int keepAliveTimeout = 60;
    /**
     * 实时数据传输配置
     */
    private EndpointOption endpoint = new EndpointOption();

    @Data
    public static class EndpointOption {
        public static final String PRE= "patch/server/";

        private String host = "127.0.0.1";
        private Integer port = 15556;
        /**
         * 补丁存放目录
         */
        private String patch;
    }
    /**
     * 获取编码
     *
     * @return {@link BootRequestEncode}
     */
    public BootRequestEncode getEncode() {
        return ServiceProvider.of(BootRequestEncode.class).getNewExtension(encryptionSchema, encryptionKey);
    }

    /**
     * 获取解码
     *
     * @return {@link BootResponseDecode}
     */
    public BootRequestDecode getDecode() {
        return ServiceProvider.of(BootRequestDecode.class).getNewExtension(encryptionSchema, encryptionKey);
    }
}
