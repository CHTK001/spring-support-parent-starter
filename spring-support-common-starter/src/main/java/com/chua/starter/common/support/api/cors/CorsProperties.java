package com.chua.starter.common.support.api.cors;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * 跨域配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
@Data
@ConfigurationProperties(prefix = "plugin.cors", ignoreInvalidFields = true)
public class CorsProperties {

    /**
     * 开启跨域
     */
    private boolean enable = true;

    /**
     * 白名单
     */
    private Set<String> pattern = new HashSet<>();
}


