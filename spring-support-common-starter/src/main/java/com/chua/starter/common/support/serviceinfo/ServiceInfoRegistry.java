package com.chua.starter.common.support.serviceinfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 服务信息全局注册表 (单例)。
 *
 * <p>由 spring-support-common-starter 提供, 任何业务 starter 都可以通过此注册表
 * 记录自己的端点 URL 和开关状态 (三态: true / false / null)。
 * 启动后由 {@link ServiceInfoAutoConfiguration} 统一渲染输出。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ServiceInfoRegistry.getInstance().registerEndpoint("分片上传", "文件系统", "POST /chunk/create", "...");
 * ServiceInfoRegistry.getInstance().registerFlag("OSS", "存储", true, "阿里云 OSS 已启用");
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ServiceInfoRegistry {

    private static final ServiceInfoRegistry INSTANCE = new ServiceInfoRegistry();

    private final Map<String, EndpointInfo> endpoints = new LinkedHashMap<>();
    private final Map<String, FlagInfo> flags = new LinkedHashMap<>();

    private ServiceInfoRegistry() {}

    public static ServiceInfoRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册/覆盖一个端点。
     *
     * @param group       分组 (例如 "文件系统", "文档")
     * @param name        端点名 (例如 "分片上传")
     * @param url         端点 URL 或方法路径
     * @param description 描述
     */
    public void registerEndpoint(String group, String name, String url, String description) {
        String key = group + "/" + name;
        endpoints.put(key, new EndpointInfo(group, name, url, description));
    }

    /**
     * 注册/覆盖一个三态开关标志。
     *
     * @param name        标志名 (例如 "swagger")
     * @param group       分组
     * @param value       true / false / null (null 表示"未配置")
     * @param description 描述
     */
    public void registerFlag(String name, String group, Boolean value, String description) {
        String key = group + "/" + name;
        flags.put(key, new FlagInfo(name, group, value, description));
    }

    public Map<String, EndpointInfo> getEndpoints() {
        return endpoints;
    }

    public Map<String, FlagInfo> getFlags() {
        return flags;
    }

    /**
     * 导出所有信息给 REST 端点。
     */
    public Map<String, Object> getInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("endpoints", endpoints);
        data.put("flags", flags);
        return data;
    }

    public static class EndpointInfo {
        private final String group;
        private final String name;
        private final String url;
        private final String description;

        public EndpointInfo(String group, String name, String url, String description) {
            this.group = group;
            this.name = name;
            this.url = url;
            this.description = description;
        }

        public String getGroup() { return group; }
        public String getName() { return name; }
        public String getUrl() { return url; }
        public String getDescription() { return description; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof EndpointInfo)) return false;
            EndpointInfo that = (EndpointInfo) o;
            return Objects.equals(group, that.group) && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, name);
        }
    }

    public static class FlagInfo {
        private final String name;
        private final String group;
        private final Boolean value;
        private final String description;

        public FlagInfo(String name, String group, Boolean value, String description) {
            this.name = name;
            this.group = group;
            this.value = value;
            this.description = description;
        }

        public String getName() { return name; }
        public String getGroup() { return group; }
        public Boolean getValue() { return value; }
        public String getDescription() { return description; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FlagInfo)) return false;
            FlagInfo that = (FlagInfo) o;
            return Objects.equals(group, that.group) && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, name);
        }
    }
}