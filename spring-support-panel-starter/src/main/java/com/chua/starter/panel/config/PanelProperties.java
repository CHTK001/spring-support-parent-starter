package com.chua.starter.panel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 面板模块配置。
 */
@Data
@ConfigurationProperties(prefix = PanelProperties.PREFIX)
public class PanelProperties {

    public static final String PREFIX = "panel";

    private boolean enabled = true;

    private Duration connectionCacheTtl = Duration.ofMinutes(15);

    private Duration documentCacheTtl = Duration.ofMinutes(30);

    private boolean jdbcEnabled = true;

    private boolean aiEnabled = true;
}
