package com.chua.starter.common.support.serviceinfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
* 服务信息全局注册表 (单例)。
*
* <p>由 spring-support-common-starter 提供, 任何业务 starter 都可以通过此注册表
* 记录自己的端点 URL 和开关状态 (三态: true / false / 空)。
* 启动后由 {@link ServiceInfoAutoConfiguration} 统一渲染输出。</p>
*
* <h3>使用示例</h3>
* <pre>{@code
* ServiceInfoRegistry.getInstance().registerEndpoint("分片上传", "文件系统", "POST /chunk/create", "...");
* ServiceInfoRegistry.getInstance().registerFlag("OSS", "存储", true, "阿里云 OSS 已启用");
* }</pre>true, "阿里云 OSS 已启用");
* }</pre>
*
* <h3>线程安全与生命周期</h3>
* <ul>
*   <li>单例生命周期与 JVM 一致, 不受 Spring 上下文关闭影响。</li>
*   <li>Map 操作使用 {@link LinkedHashMap}, 非线程安全。
*       调用方应保证在 Spring 容器初始化阶段 (主线程) 调用,
*       不要在运行时并发注册。</li>
*   <li>多 {@code ApplicationContext} 场景 (如 Spring Boot 测试嵌套上下文)
*       会共享同一 Registry 实例, 可能造成状态污染。
*       测试场景可调用 {@link #reset()} 清空。</li>
* </ul>
*
* @since 4.0.0.42
* @author CH
 */
public final class ServiceInfoRegistry {

    /** 实例 */
    private static final ServiceInfoRegistry INSTANCE = new ServiceInfoRegistry();

    /**
    * 映射 键 分隔符: 使用 NUL 字符避免与 {@code group} 或 {@code name} 中的
    * 常见字符 (如 '/'、'-') 冲突。
    */
    private static final String KEY_SEP = "\u0000";

    private final Map<String, EndpointInfo> endpoints = new LinkedHashMap<>(); // 端点
    private final Map<String, FlagInfo> flags = new LinkedHashMap<>(); // flags

    /** 创建 服务信息registry 实例 */
    private ServiceInfoRegistry() {}

    /**
    * 获取Instance
    *
    * @return 获取instance的结果
    */
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
        String key = keyOf(group, name);
        endpoints.put(key, new EndpointInfo(group, name, url, description));
    }

    /**
    * 注册/覆盖一个三态开关标志。
    *
    * @param name        标志名 (例如 "Swagger")
    * @param group       分组
    * @param value       true / false / 空 (空 表示"未配置")
    * @param description 描述
    */
    public void registerFlag(String name, String group, Boolean value, String description) {
        registerFlag(name, group, value, description, null);
    }

    /**
    * 注册/覆盖一个带附加上下文的开关标志。
    *
    * <p>用于呈现类似 {@code [开启] [平台: system]} 这种带运行信息的开关。
    * 横幅输出时会原样附加在开关名后。</p>
    *
    * @param name        标志名
    * @param group       分组
    * @param value       true / false / 空
    * @param description 描述
    * @param suffix      附加上下文 (例如 {@code "[平台: system]"}、{@code "[环境: dev,test]"}); 为 空 时不输出
    */
    public void registerFlag(String name, String group, Boolean value, String description, String suffix) {
        String key = keyOf(group, name);
        flags.put(key, new FlagInfo(name, group, value, description, suffix));
    }

    /**
    * 键的
    *
    * @param group 群体
    * @param name 名称
    * @return 键的的结果
    */
    private static String keyOf(String group, String name) {
        return (group == null ? "" : group) + KEY_SEP + (name == null ? "" : name);
    }

    /**
    * 根据 群体/名称 查找已注册的开关。
    *
    * @param group 分组
    * @param name  开关名
    * @return 找到的 flag信息, 未找到返回 空
    */
    public FlagInfo findFlag(String group, String name) {
        if (group == null || name == null) {
            return null;
        }
        return flags.get(keyOf(group, name));
    }

    /**
    * 根据 群体/名称 查找已注册的端点。
    *
    * @param group 分组
    * @param name  端点名
    * @return 找到的 端点信息, 未找到返回 空
    */
    public EndpointInfo findEndpoint(String group, String name) {
        if (group == null || name == null) {
            return null;
        }
        return endpoints.get(keyOf(group, name));
    }

    /**
    * 获取端点
    *
    * @return 获取端点的结果
    */
    public Map<String, EndpointInfo> getEndpoints() {
        return endpoints;
    }

    /**
    * 获取Flags
    *
    * @return 获取flags的结果
    */
    public Map<String, FlagInfo> getFlags() {
        return flags;
    }

    /**
    * 导出所有信息给 REST 端点。
    * @return 获取信息的结果
    */
    public Map<String, Object> getInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("endpoints", endpoints);
        data.put("flags", flags);
        return data;
    }

    /**
    * 清空所有已注册的端点和开关。
    *
    * <p>仅供测试场景使用; 生产代码不应调用此方法。</p>
    * @author CH
    * @since 4.0.0
    */
    public void reset() {
        endpoints.clear();
        flags.clear();
    }

    public static class EndpointInfo {
        /** 分组 */
        private final String group;
        /** 名称 */
        private final String name;
        /** URL */
        private final String url;
        /** 描述 */
        private final String description;

        /**
        * 创建 端点信息 实例
        * @param group 群体
        * @param group 字符串
        * @param group 字符串
        * @param group 字符串
        * @param name 名称
        * @param url url
        * @param description description
        */
        public EndpointInfo(String group, String name, String url, String description) {
            this.group = group;
            this.name = name;
            this.url = url;
            this.description = description;
        }

        /**
        * 获取分组
        *
        * @return 获取群体的结果
        */
        public String getGroup() { return group; }
        /**
        * 获取名称
        *
        * @return 获取名称的结果
        */
        public String getName() { return name; }
        /**
        * 获取Url
        *
        * @return 获取url的结果
        */
        public String getUrl() { return url; }
        /**
        * 获取Description
        *
        * @return 获取description的结果
        */
        public String getDescription() { return description; }

        @Override
        /** 判断相等 */
        public boolean equals(Object o) {
            if (!(o instanceof EndpointInfo)) {
                return false;
            }
            EndpointInfo that = (EndpointInfo) o;
            return Objects.equals(group, that.group) && Objects.equals(name, that.name);
        }

        @Override
        /**
        * 哈希编码
        *
        * @return 哈希编码的结果
        * @author CH
        * @since 4.0.0
        */
        public int hashCode() {
            return Objects.hash(group, name);
        }
    }

    public static class FlagInfo {
        /** 名称 */
        private final String name;
        /** 分组 */
        private final String group;
        /** 值 */
        private final Boolean value;
        /** 描述 */
        private final String description;
        /** 后缀 */
        private final String suffix;

        /**
        * 创建 flag信息 实例
        * @param name 名称
        * @param name 字符串
        * @param value 布尔值
        * @param name 字符串
        * @param name 字符串
        * @param group 群体
        * @param value 值
        * @param description description
        * @param suffix 后缀
        */
        public FlagInfo(String name, String group, Boolean value, String description, String suffix) {
            this.name = name;
            this.group = group;
            this.value = value;
            this.description = description;
            this.suffix = suffix;
        }

        /**
        * 获取名称
        *
        * @return 获取名称的结果
        */
        public String getName() { return name; }
        /**
        * 获取分组
        *
        * @return 获取群体的结果
        */
        public String getGroup() { return group; }
        /**
        * 获取值
        *
        * @return 获取值的结果
        */
        public Boolean getValue() { return value; }
        /**
        * 获取Description
        *
        * @return 获取description的结果
        */
        public String getDescription() { return description; }
        /**
        * 获取后缀
        *
        * @return 获取后缀的结果
        */
        public String getSuffix() { return suffix; }

        @Override
        /** 判断相等 */
        public boolean equals(Object o) {
            if (!(o instanceof FlagInfo)) {
                return false;
            }
            FlagInfo that = (FlagInfo) o;
            return Objects.equals(group, that.group) && Objects.equals(name, that.name);
        }

        @Override
        /** 哈希编码 */
        public int hashCode() {
            return Objects.hash(group, name);
        }
    }
}
