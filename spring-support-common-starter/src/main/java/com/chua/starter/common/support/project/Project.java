package com.chua.starter.common.support.project;

import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.net.NetAddress;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.NumberUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * 项目
 * @author CH
 * @since 2024/9/6
 */
@Data
@NoArgsConstructor
public class Project {
    public static final String KEY = "oo00OOOO00ooll11";

    private static final Project INSTANCE = new Project();
    /**
     * 应用名称
     */
    private String applicationName;

    /**
     * 应用端口
     */
    private Integer applicationPort;

    /**
     * 应用地址
     */
    private String applicationHost;

    /**
     * 应用环境
     */
    private String applicationActive;

    /**
     * 应用环境
     */
    private String applicationActiveInclude;
    /**
     * 上下文路径
     */
    private String contextPath;
    /**
     * 端点地址
     */
    private String endpointsUrl;

    /**
     * 端点
     */
    private String endpoints;

    /**
     * 数据库地址
     */
    private String dataSourceUrl;

    /**
     * 驱动
     */
    private String dataSourceDriver;
    /**
     * 用户名
     */
    private String dataSourceUsername;
    /**
     * 密码
     */
    private String dataSourcePassword;
    /**
     * 环境
     */
    private Environment environment;
    /**
     * 客户端绑定的服务端口
     */
    private String clientProtocolEndpointPort;

    /**
     * 客户端绑定的服务协议
     */
    private String clientProtocolEndpointProtocol;

    public Project(Map<String, String> metadata) {
        this.applicationName = MapUtils.getString(metadata, "applicationName");
        this.applicationPort = NumberUtils.toInt(MapUtils.getString(metadata, "applicationPort"));
        this.applicationHost = MapUtils.getString(metadata, "applicationHost");
        this.applicationActive = MapUtils.getString(metadata, "applicationActive");
        this.applicationActiveInclude = MapUtils.getString(metadata, "applicationActiveInclude");
        this.contextPath = MapUtils.getString(metadata, "contextPath");
        this.endpointsUrl = MapUtils.getString(metadata, "endpointsUrl");
        this.endpoints = MapUtils.getString(metadata, "endpoints");
        this.dataSourceUrl = MapUtils.getString(metadata, "dataSourceUrl");
        this.dataSourceDriver = MapUtils.getString(metadata, "dataSourceDriver");
        this.dataSourceUsername = MapUtils.getString(metadata, "dataSourceUsername");
        this.dataSourcePassword = DigestUtils.aesDecrypt(MapUtils.getString(metadata, "dataSourcePassword"), KEY);
        this.clientProtocolEndpointPort = MapUtils.getString(metadata, "clientProtocolEndpointPort");
        this.clientProtocolEndpointProtocol = MapUtils.getString(metadata, "clientProtocolEndpointProtocol");
    }


    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.applicationName = environment.getProperty("spring.application.name");
        this.applicationPort = NumberUtils.toInt(environment.resolvePlaceholders("${server.port:8080}"));
        String localHost = NetUtils.getLocalHost();
        this.applicationHost = environment.resolvePlaceholders("${server.host:"+ localHost+"}");
        this.applicationActive = environment.getProperty("spring.profiles.active", "default");
        this.applicationActiveInclude = environment.getProperty("spring.profiles.include", "");
        this.contextPath = environment.resolvePlaceholders("${server.servlet.context-path:}");
        this.endpointsUrl = environment.resolvePlaceholders("${management.endpoints.web.base-path:/actuator}");
        this.endpoints = environment.resolvePlaceholders("${management.endpoints.web.exposure.include:*}");
        DataSourceProperties dataSourceProperties = Binder.get(environment).bindOrCreate("spring.datasource", DataSourceProperties.class);
        this.dataSourceUrl = dataSourceProperties.getUrl();
        this.dataSourceDriver = dataSourceProperties.getDriverClassName();
        this.dataSourceUsername = dataSourceProperties.getUsername();
        this.dataSourcePassword = DigestUtils.aesEncrypt(dataSourceProperties.getPassword(), KEY);
        this.clientProtocolEndpointPort = environment.resolvePlaceholders("${plugin.report.client.endpoint.port:18080}");
        this.clientProtocolEndpointProtocol = environment.resolvePlaceholders("${plugin.report.client.endpoint.protocol:http}");

    }

    public static Project getInstance() {
        return INSTANCE;
    }

    /**
     * 计算应用uuid
     * @return uuid
     */
    public String calcApplicationUuid() {
        NetAddress netAddress = NetAddress.of(dataSourceUrl);
        return DigestUtils.md5Hex(dataSourceDriver + netAddress.getNoParamAddress() + dataSourceUsername + dataSourcePassword);
    }

    public Map<String, String> getProject() {
        return ImmutableBuilder.builderOfStringStringMap()
                .put("applicationName", applicationName)
                .put("applicationPort", String.valueOf(applicationPort))
                .put("applicationHost", applicationHost)
                .put("applicationActive", applicationActive)
                .put("applicationActiveInclude", applicationActiveInclude)
                .put("contextPath", contextPath)
                .put("dataSourceUrl", dataSourceUrl)
                .put("reportEndpointPort", clientProtocolEndpointPort)
                .put("dataSourceDriver", dataSourceDriver)
                .put("dataSourceUsername", dataSourceUsername)
                .put("dataSourcePassword", dataSourcePassword)
                .put("endpointsUrl", endpointsUrl).asSynchronizedMap();
    }
}
