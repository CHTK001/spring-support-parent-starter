package com.chua.report.client.starter.properties;

import com.chua.report.client.starter.endpoint.ModuleType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * 上报配置
 * @author CH
 * @since 2024/9/11
 */
@Data
@ConfigurationProperties(prefix = ReportEndpointProperties.PRE, ignoreInvalidFields = true)
public class ReportEndpointProperties {

    public static final String PRE = "plugin.report.client.endpoint";


    /**
     * 协议
     */
    private String protocol = "http";

    /**
     * 端点端口
     */
    private Integer port = -1;

    /**
     * 激活那些功能
     *  - config
     */
    private Set<ModuleType> active = new HashSet<>();
}
