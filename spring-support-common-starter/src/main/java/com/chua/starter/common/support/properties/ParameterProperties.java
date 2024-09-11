package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一参数
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.parameter", ignoreInvalidFields = true)
public class ParameterProperties {

    /**
     * 开启返回结果一致性
     * @see com.chua.common.support.lang.code.ReturnResult
     */
    private boolean enable;
}