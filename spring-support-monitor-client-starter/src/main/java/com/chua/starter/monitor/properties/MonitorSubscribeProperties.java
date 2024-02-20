package com.chua.starter.monitor.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static com.chua.starter.monitor.properties.MonitorSubscribeProperties.PRE;

/**
 * 监视器属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class MonitorSubscribeProperties {

    public static final String PRE = "plugin.monitor.subscribe";

    /**
     * 订阅的配置名称, 默不订阅
     */
    private List<String> config;

    /**
     * 补丁存放位置
     */
    private String hotspot = "./patch/client/";
}
