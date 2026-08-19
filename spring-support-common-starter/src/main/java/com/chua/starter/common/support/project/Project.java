package com.chua.starter.common.support.project;

import com.chua.starter.common.support.algorithm.crypto.Codec;
import com.chua.common.support.network.net.NetAddress;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

/**
 * 项目
 * @since 2024/9/6
 * @author CH
 */
public class Project {
    /**
     * 无参构造函数
     */
    public Project() {
    }

    /** 密钥 */
    public static final String KEY = "oo00OOOO00ooll11";

    /** 实例 */
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
        this.clientProtocolEndpointPort = MapUtils.getString(metadata, "clientProtocolEndpointPort");
        this.clientProtocolEndpointProtocol = MapUtils.getString(metadata, "clientProtocolEndpointProtocol");
    }


    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.applicationName = environment.getProperty("spring.application.name");
        this.applicationPort = NumberUtils.toInt(environment.resolvePlaceholders("${server.port:8080}"));
        String localHost;
        try {
            localHost = InetAddress.getLocalHost().getHostAddress();
        } catch (java.net.UnknownHostException e) {
            localHost = "127.0.0.1";
        }
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
        this.clientProtocolEndpointPort = StringUtils.defaultString(environment.resolvePlaceholders("${plugin.report.client.endpoint.port:}"), String.valueOf(NumberUtils.toInt(environment.resolvePlaceholders("${server.port:8080}")) + 10000));
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
        return DigestUtils.md5(dataSourceDriver + netAddress.getAddress() + dataSourceUsername + dataSourcePassword);
    }

    public Map<String, String> getProject() {
        java.util.Map<String, String> project = new java.util.LinkedHashMap<>();
        project.put("applicationName", applicationName);
        project.put("applicationPort", String.valueOf(applicationPort));
        project.put("applicationHost", applicationHost);
        project.put("applicationActive", applicationActive);
        project.put("applicationActiveInclude", applicationActiveInclude);

        project.put("contextPath", contextPath);

        project.put("dataSourceUrl", dataSourceUrl);
        project.put("dataSourceDriver", dataSourceDriver);
        project.put("dataSourceUsername", dataSourceUsername);
        project.put("dataSourcePassword", dataSourcePassword);

        project.put("clientProtocolEndpointPort", clientProtocolEndpointPort);
        project.put("clientProtocolEndpointProtocol", clientProtocolEndpointProtocol);

        project.put("endpoints", endpoints);
        project.put("endpointsUrl", endpointsUrl);
        return Collections.unmodifiableMap(project);
    }
    /**
     * 获取 applicationName
     *
     * @return applicationName
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * 设置 applicationName
     *
     * @param applicationName applicationName
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * 获取 applicationPort
     *
     * @return applicationPort
     */
    public Integer getApplicationPort() {
        return applicationPort;
    }

    /**
     * 设置 applicationPort
     *
     * @param applicationPort applicationPort
     */
    public void setApplicationPort(Integer applicationPort) {
        this.applicationPort = applicationPort;
    }

    /**
     * 获取 applicationHost
     *
     * @return applicationHost
     */
    public String getApplicationHost() {
        return applicationHost;
    }

    /**
     * 设置 applicationHost
     *
     * @param applicationHost applicationHost
     */
    public void setApplicationHost(String applicationHost) {
        this.applicationHost = applicationHost;
    }

    /**
     * 获取 applicationActive
     *
     * @return applicationActive
     */
    public String getApplicationActive() {
        return applicationActive;
    }

    /**
     * 设置 applicationActive
     *
     * @param applicationActive applicationActive
     */
    public void setApplicationActive(String applicationActive) {
        this.applicationActive = applicationActive;
    }

    /**
     * 获取 applicationActiveInclude
     *
     * @return applicationActiveInclude
     */
    public String getApplicationActiveInclude() {
        return applicationActiveInclude;
    }

    /**
     * 设置 applicationActiveInclude
     *
     * @param applicationActiveInclude applicationActiveInclude
     */
    public void setApplicationActiveInclude(String applicationActiveInclude) {
        this.applicationActiveInclude = applicationActiveInclude;
    }

    /**
     * 获取 contextPath
     *
     * @return contextPath
     */
    public String getContextPath() {
        return contextPath;
    }

    /**
     * 设置 contextPath
     *
     * @param contextPath contextPath
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * 获取 endpointsUrl
     *
     * @return endpointsUrl
     */
    public String getEndpointsUrl() {
        return endpointsUrl;
    }

    /**
     * 设置 endpointsUrl
     *
     * @param endpointsUrl endpointsUrl
     */
    public void setEndpointsUrl(String endpointsUrl) {
        this.endpointsUrl = endpointsUrl;
    }

    /**
     * 获取 endpoints
     *
     * @return endpoints
     */
    public String getEndpoints() {
        return endpoints;
    }

    /**
     * 设置 endpoints
     *
     * @param endpoints endpoints
     */
    public void setEndpoints(String endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * 获取 dataSourceUrl
     *
     * @return dataSourceUrl
     */
    public String getDataSourceUrl() {
        return dataSourceUrl;
    }

    /**
     * 设置 dataSourceUrl
     *
     * @param dataSourceUrl dataSourceUrl
     */
    public void setDataSourceUrl(String dataSourceUrl) {
        this.dataSourceUrl = dataSourceUrl;
    }

    /**
     * 获取 dataSourceDriver
     *
     * @return dataSourceDriver
     */
    public String getDataSourceDriver() {
        return dataSourceDriver;
    }

    /**
     * 设置 dataSourceDriver
     *
     * @param dataSourceDriver dataSourceDriver
     */
    public void setDataSourceDriver(String dataSourceDriver) {
        this.dataSourceDriver = dataSourceDriver;
    }

    /**
     * 获取 dataSourceUsername
     *
     * @return dataSourceUsername
     */
    public String getDataSourceUsername() {
        return dataSourceUsername;
    }

    /**
     * 设置 dataSourceUsername
     *
     * @param dataSourceUsername dataSourceUsername
     */
    public void setDataSourceUsername(String dataSourceUsername) {
        this.dataSourceUsername = dataSourceUsername;
    }

    /**
     * 获取 dataSourcePassword
     *
     * @return dataSourcePassword
     */
    public String getDataSourcePassword() {
        return dataSourcePassword;
    }

    /**
     * 设置 dataSourcePassword
     *
     * @param dataSourcePassword dataSourcePassword
     */
    public void setDataSourcePassword(String dataSourcePassword) {
        this.dataSourcePassword = dataSourcePassword;
    }

    /**
     * 获取 environment
     *
     * @return environment
     */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * 获取 clientProtocolEndpointPort
     *
     * @return clientProtocolEndpointPort
     */
    public String getClientProtocolEndpointPort() {
        return clientProtocolEndpointPort;
    }

    /**
     * 设置 clientProtocolEndpointPort
     *
     * @param clientProtocolEndpointPort clientProtocolEndpointPort
     */
    public void setClientProtocolEndpointPort(String clientProtocolEndpointPort) {
        this.clientProtocolEndpointPort = clientProtocolEndpointPort;
    }

    /**
     * 获取 clientProtocolEndpointProtocol
     *
     * @return clientProtocolEndpointProtocol
     */
    public String getClientProtocolEndpointProtocol() {
        return clientProtocolEndpointProtocol;
    }

    /**
     * 设置 clientProtocolEndpointProtocol
     *
     * @param clientProtocolEndpointProtocol clientProtocolEndpointProtocol
     */
    public void setClientProtocolEndpointProtocol(String clientProtocolEndpointProtocol) {
        this.clientProtocolEndpointProtocol = clientProtocolEndpointProtocol;
    }

    private static String aesEncrypt(String content, String key) {
        if (content == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "AES"));
            return Base64.getEncoder().encodeToString(cipher.doFinal(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String aesDecrypt(String content, String key) {
        if (content == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "AES"));
            return new String(cipher.doFinal(Base64.getDecoder().decode(content)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
