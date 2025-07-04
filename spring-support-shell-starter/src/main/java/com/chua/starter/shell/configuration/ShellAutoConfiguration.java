package com.chua.starter.shell.configuration;

import com.chua.starter.shell.properties.ShellProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 *
 * shell配置
 * @author CH
 * @since 2025/7/4 16:48
 */
@EnableConfigurationProperties(ShellProperties.class)
public class ShellAutoConfiguration {
}
