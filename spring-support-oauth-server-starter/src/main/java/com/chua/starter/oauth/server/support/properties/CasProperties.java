package com.chua.starter.oauth.server.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cas配置
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.oauth")
public class CasProperties {
    /**
     * 登录地址
     */
    private String newLoginUrl;
    /**
     * 主页地址
     */
    private String newIndexUrl;
}
