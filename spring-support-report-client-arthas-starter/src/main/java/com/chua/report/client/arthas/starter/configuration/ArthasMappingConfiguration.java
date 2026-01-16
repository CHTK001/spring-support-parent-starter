package com.chua.report.client.arthas.starter.configuration;

import com.chua.common.support.invoke.annotation.GetRequestLine;
import com.chua.common.support.protocol.request.HttpServletResponse;
import com.chua.report.client.arthas.starter.properties.ArthasClientProperties;

/**
 * Arthas 映射：对外暴露查询本地 Arthas 配置
 */
public class ArthasMappingConfiguration {

    private final ArthasClientProperties properties;

    public ArthasMappingConfiguration(ArthasClientProperties properties) {
        this.properties = properties;
    }

    @GetRequestLine("arthas-config")
    public com.chua.common.support.protocol.request.ServletResponse config(
            com.chua.common.support.protocol.request.ServletRequest request) {
        return HttpServletResponse.ok("{\"enable\":" + properties.isEnable() + ",\"tunnelAddress\":\"" +
                (properties.getTunnelAddress() == null ? "" : properties.getTunnelAddress()) + "\"}");
    }
}


