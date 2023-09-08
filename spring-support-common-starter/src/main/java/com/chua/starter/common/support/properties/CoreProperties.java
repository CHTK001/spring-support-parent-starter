package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.common.support.properties.CoreProperties.PRE;

/**
 * 跨域
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class CoreProperties {

    public static final String PRE = "plugin.core";
    /**
     * 是否开启统一响应(返回值自动包裹对象)
     */
    private boolean uniformParameter = true;

    /**
     * 开启请求参数日志打印
     */
    private boolean openParamLog = true;
    /**
     * 是否开启版本控制
     */
    private boolean openVersion;
    /**
     * 开启编排接口
     */
    private boolean openArrange = true;

    /**
     * geo
     */
    private Geo geo = new Geo();

    @Data
    public class Geo {
        /**
         * 系统资源目录
         */
        private String config = "/";
        /**
         * 实现方式
         */
        private String impl = "geo";
    }
}