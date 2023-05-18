package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * 跨域
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.cors", ignoreInvalidFields = true)
public class CorsProperties {

    /**
     * 开启跨域
     */
    private boolean enable;
    /**
     * 白名单
     */
    private Set<String> pattern = new HashSet<>();
}
