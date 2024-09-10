package com.chua.starter.common.support.project;

import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.NumberUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * 项目
 * @author CH
 * @since 2024/9/6
 */
@Data
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class Project {


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
     * 环境
     */
    private Environment environment;

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
    }

    public static Project getInstance() {
        return INSTANCE;
    }

    /**
     * 计算应用uuid
     * @return uuid
     */
    public String calcApplicationUuid() {
        return DigestUtils.md5Hex(applicationHost + applicationPort);
    }

    public Map<String, String> getProject() {
        return ImmutableBuilder.builderOfStringStringMap()
                .put("applicationName", applicationName)
                .put("applicationPort", String.valueOf(applicationPort))
                .put("applicationHost", applicationHost)
                .put("applicationActive", applicationActive)
                .put("applicationActiveInclude", applicationActiveInclude)
                .put("contextPath", contextPath)
                .put("endpointsUrl", endpointsUrl).asSynchronizedMap();
    }
}
