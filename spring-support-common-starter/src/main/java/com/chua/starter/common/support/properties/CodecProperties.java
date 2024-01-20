package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全局常数
 *
 * @author CH
 */

@Data
@ConfigurationProperties(prefix = CodecProperties.PRE, ignoreInvalidFields = true)
public class CodecProperties {

    public static final String PRE = "plugin.codec";

    /**
     * 开放式编解码器
     */
    private boolean enable = true;

    /**
     * 编解码器类型
     */
    private String codecType = "sm2";

    private String privateKey;

    private String publicKey;

    
}
