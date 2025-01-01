package com.chua.starter.common.support.properties;

import com.chua.common.support.os.PlatformKey;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.common.support.properties.ControlProperties.PRE;

/**
 * 跨域/版本控制/统一响应
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class ControlProperties {

    public static final String PRE = "plugin.control";


    /**
     * 是否开启版本控制
     */
    private Version version = new Version();

    /**
     * 平台名称
     */
    private Platform platform = new Platform();

    public boolean isEnable() {
        return null != version && version.isEnable()
                || (null != platform && platform.isEnable());
    }


    @Data
    public static class Platform {
        /**
         * 是否开启版本控制
         */
        private boolean enable = true;


        /**
         * 当前平台名称
         */
        private String name = "system";
    }

    @Data
    public static class Version {
        /**
         * 是否开启版本控制
         */
        private boolean enable = false;


        /**
         * 版本号
         */
        private String name;
    }


}