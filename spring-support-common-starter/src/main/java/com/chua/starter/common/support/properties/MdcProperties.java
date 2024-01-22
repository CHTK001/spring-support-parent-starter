package com.chua.starter.common.support.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息转换器属性
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = MdcProperties.PRE, ignoreInvalidFields = true)
public class MdcProperties {

    public static final String PRE = "plugin.mdc";



    /**
     * 开启脱敏
     */
    private boolean enable = true;

}
