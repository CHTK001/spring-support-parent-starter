package com.chua.starter.server.support.config.environment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上传限制环境后处理器
 *
 * <p>发布管理模块（{@code /ms/server/sftp/**} 上传、{@code /ms/server/release/project/{id}/upload}
 * 拖拽升级）需要传递发布包，发布包普遍是上百 MB 的 jar。若沿用 Spring Boot 默认的
 * {@code spring.servlet.multipart.max-file-size=1MB}，任何超过 1MB 的安装包都会被
 * {@code MaxUploadSizeExceededException} 拒绝，表现为 HTTP 413 / {@code F0413S0001 文件超过大小限制}，
 * 而该报错文案完全无法提示真实原因。</p>
 *
 * <p>同时 Tomcat 侧 {@code max-swallow-size}（默认 2MB）会在请求被拒后强行截断连接，
 * 导致大文件上传即便放开 multipart 限制也会中途断开，因此需要一并放开。</p>
 *
 * <p>本处理器以 {@link MapPropertySource} 追加到属性源列表末尾，<b>仅在用户未显式配置时</b>提供默认值，
 * 因此任何显式配置（含 application.yml、命令行参数、环境变量）仍然优先生效，不覆盖用户意图。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public class ServerUploadEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /**
     * 属性源名称
     */
    private static final String PROPERTY_SOURCE_NAME = "server-upload-defaults";

    /**
     * 上传大小默认值（2GB）
     */
    private static final String DEFAULT_MAX_SIZE = "2GB";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        putIfAbsent(environment, defaults, "spring.servlet.multipart.enabled", "true");
        putIfAbsent(environment, defaults, "spring.servlet.multipart.max-file-size", DEFAULT_MAX_SIZE);
        putIfAbsent(environment, defaults, "spring.servlet.multipart.max-request-size", DEFAULT_MAX_SIZE);
        putIfAbsent(environment, defaults, "server.tomcat.max-swallow-size", DEFAULT_MAX_SIZE);
        putIfAbsent(environment, defaults, "server.tomcat.max-http-form-post-size", DEFAULT_MAX_SIZE);
        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    /**
     * 仅当环境与配置数据中都不存在该键时写入默认值
     *
     * @param environment 可配置环境
     * @param defaults    默认值容器
     * @param key         属性键
     * @param value       默认值
     */
    private void putIfAbsent(ConfigurableEnvironment environment, Map<String, Object> defaults, String key, String value) {
        if (!environment.containsProperty(key)) {
            defaults.put(key, value);
        }
    }
}
