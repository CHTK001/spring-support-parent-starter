// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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

      /**
       * 密钥
      */
    public static final String KEY = "oo00OOOO00ooll11";

      /**
       * 实例
      */
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

      /**
       * 创建 Project 实例
       * @param metadata 字符串
       * @param metadata metadata
       */
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


      /**
       * 设置环境
       *
       * @param environment 环境
       */
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

      /**
       * 获取Instance
       *
       * @return 获取instance的结果
       */
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

      /**
       * 获取Project
       *
       * @return 获取project的结果
       */
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
       * 获取 application名称
       *
       * @return applicationName
       */
    public String getApplicationName() {
        return applicationName;
    }

      /**
       * 设置 application名称
       *
       * @param applicationName application名称
       */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

      /**
       * 获取 application端口
       *
       * @return applicationPort
       */
    public Integer getApplicationPort() {
        return applicationPort;
    }

      /**
       * 设置 application端口
       *
       * @param applicationPort application端口
       */
    public void setApplicationPort(Integer applicationPort) {
        this.applicationPort = applicationPort;
    }

      /**
       * 获取 application主机
       *
       * @return applicationHost
       */
    public String getApplicationHost() {
        return applicationHost;
    }

      /**
       * 设置 application主机
       *
       * @param applicationHost application主机
       */
    public void setApplicationHost(String applicationHost) {
        this.applicationHost = applicationHost;
    }

      /**
       * 获取 application活跃
       *
       * @return applicationActive
       */
    public String getApplicationActive() {
        return applicationActive;
    }

      /**
       * 设置 application活跃
       *
       * @param applicationActive application活跃
       */
    public void setApplicationActive(String applicationActive) {
        this.applicationActive = applicationActive;
    }

      /**
       * 获取 application活跃include
       *
       * @return applicationActiveInclude
       */
    public String getApplicationActiveInclude() {
        return applicationActiveInclude;
    }

      /**
       * 设置 application活跃include
       *
       * @param applicationActiveInclude application活跃include
       */
    public void setApplicationActiveInclude(String applicationActiveInclude) {
        this.applicationActiveInclude = applicationActiveInclude;
    }

      /**
       * 获取 上下文路径
       *
       * @return contextPath
       */
    public String getContextPath() {
        return contextPath;
    }

      /**
       * 设置 上下文路径
       *
       * @param contextPath 上下文路径
       */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

      /**
       * 获取 端点url
       *
       * @return endpointsUrl
       */
    public String getEndpointsUrl() {
        return endpointsUrl;
    }

      /**
       * 设置 端点url
       *
       * @param endpointsUrl 端点url
       */
    public void setEndpointsUrl(String endpointsUrl) {
        this.endpointsUrl = endpointsUrl;
    }

      /**
       * 获取 端点
       *
       * @return endpoints
       */
    public String getEndpoints() {
        return endpoints;
    }

      /**
       * 设置 端点
       *
       * @param endpoints 端点
       */
    public void setEndpoints(String endpoints) {
        this.endpoints = endpoints;
    }

      /**
       * 获取 数据源url
       *
       * @return dataSourceUrl
       */
    public String getDataSourceUrl() {
        return dataSourceUrl;
    }

      /**
       * 设置 数据源url
       *
       * @param dataSourceUrl 数据源url
       */
    public void setDataSourceUrl(String dataSourceUrl) {
        this.dataSourceUrl = dataSourceUrl;
    }

      /**
       * 获取 数据源driver
       *
       * @return dataSourceDriver
       */
    public String getDataSourceDriver() {
        return dataSourceDriver;
    }

      /**
       * 设置 数据源driver
       *
       * @param dataSourceDriver 数据源driver
       */
    public void setDataSourceDriver(String dataSourceDriver) {
        this.dataSourceDriver = dataSourceDriver;
    }

      /**
       * 获取 数据源用户名
       *
       * @return dataSourceUsername
       */
    public String getDataSourceUsername() {
        return dataSourceUsername;
    }

      /**
       * 设置 数据源用户名
       *
       * @param dataSourceUsername 数据源用户名
       */
    public void setDataSourceUsername(String dataSourceUsername) {
        this.dataSourceUsername = dataSourceUsername;
    }

      /**
       * 获取 数据源密码
       *
       * @return dataSourcePassword
       */
    public String getDataSourcePassword() {
        return dataSourcePassword;
    }

      /**
       * 设置 数据源密码
       *
       * @param dataSourcePassword 数据源密码
       */
    public void setDataSourcePassword(String dataSourcePassword) {
        this.dataSourcePassword = dataSourcePassword;
    }

      /**
       * 获取 环境
       *
       * @return environment
       */
    public Environment getEnvironment() {
        return environment;
    }

      /**
       * 获取 客户端协议端点端口
       *
       * @return clientProtocolEndpointPort
       */
    public String getClientProtocolEndpointPort() {
        return clientProtocolEndpointPort;
    }

      /**
       * 设置 客户端协议端点端口
       *
       * @param clientProtocolEndpointPort 客户端协议端点端口
       */
    public void setClientProtocolEndpointPort(String clientProtocolEndpointPort) {
        this.clientProtocolEndpointPort = clientProtocolEndpointPort;
    }

      /**
       * 获取 客户端协议端点协议
       *
       * @return clientProtocolEndpointProtocol
       */
    public String getClientProtocolEndpointProtocol() {
        return clientProtocolEndpointProtocol;
    }

      /**
       * 设置 客户端协议端点协议
       *
       * @param clientProtocolEndpointProtocol 客户端协议端点协议
       */
    public void setClientProtocolEndpointProtocol(String clientProtocolEndpointProtocol) {
        this.clientProtocolEndpointProtocol = clientProtocolEndpointProtocol;
    }

      /**
       * aesencrypt
       *
       * @param content 内容
       * @param key Key
       * @return aesEncrypt的结果
       */
    private static String aesEncrypt(String content, String key) {
        if (content == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "AES"));
            return Base64.getEncoder().encodeToString(cipher.doFinal(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败: " + e.getMessage(), e);
        }
    }

      /**
       * aesdecrypt
       *
       * @param content 内容
       * @param key Key
       * @return aesDecrypt的结果
       */
    private static String aesDecrypt(String content, String key) {
        if (content == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "AES"));
              /*
               * 解密后按 UTF-8 还原字符串。
               */
            return new String(cipher.doFinal(Base64.getDecoder().decode(content)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败: " + e.getMessage(), e);
        }
    }

}
