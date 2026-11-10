package com.chua.starter.server.support.util;

import com.chua.starter.server.support.entity.MsServerProject;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Docker 项目类型的具体参数（来自项目 {@code appConfig} 中的 JSON 对象）。
 *
 * <p>Docker 项目与 Java 项目共用 {@code appConfig} 列，但存的是<b>对象</b>而不是
 * 环境变量数组（{@code [{"key":..,"value":..}]}）：发布链路里
 * {@code readEnvList} 只认数组，对象解析失败会返回空列表，因此 Docker 的配置
 * 不会被误当成环境变量写进 {@code config/application.properties}。</p>
 *
 * <p>支持的键（全部可省略）：</p>
 * <ul>
 *   <li>{@code dockerfile} Dockerfile 路径（相对项目地址，默认 {@code Dockerfile}）</li>
 *   <li>{@code context} 构建上下文（相对项目地址，默认 {@code .}）</li>
 *   <li>{@code image} 镜像名（默认 {@code <应用名>:latest}）</li>
 *   <li>{@code container} 容器名（默认 {@code <应用名>}）</li>
 *   <li>{@code ports} 端口映射，数组或空格/换行分隔，如 {@code 8080:8080}</li>
 *   <li>{@code env} 环境变量，数组或空白分隔，如 {@code TZ=Asia/Shanghai}</li>
 *   <li>{@code volumes} 数据卷，数组或空白分隔，如 {@code /data/app:/app/data}</li>
 *   <li>{@code network} 网络名</li>
 *   <li>{@code restart} 重启策略（默认 {@code unless-stopped}）</li>
 *   <li>{@code buildArgs} 构建参数，数组或空白分隔，如 {@code VERSION=1.0}</li>
 *   <li>{@code command} 覆盖容器启动命令（整段原样追加到 {@code docker run} 末尾）</li>
 * </ul>
 *
 * <p>所有值都会做字符白名单过滤（只允许字母、数字与 {@code _ . : / \\ = @ , + -} 及空白），
 * 因为最终要拼进目标主机上的 shell 命令；带引号、分号、管道、{@code $} 等会被丢弃，
 * 避免把项目配置变成命令注入入口。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class DockerProjectSettings {

    /** appConfig 中 Docker 配置的默认值：Dockerfile 文件名 */
    private static final String DEFAULT_DOCKERFILE = "Dockerfile";
    /** 默认构建上下文 */
    private static final String DEFAULT_CONTEXT = ".";
    /** 默认重启策略 */
    private static final String DEFAULT_RESTART = "unless-stopped";
    /** 默认镜像后缀 */
    private static final String LATEST = ":latest";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 原始配置（键名与 appConfig 中一致，缺失时不做补全） */
    private final Map<String, String> values;

    private DockerProjectSettings(Map<String, String> values) {
        this.values = values;
    }

    /**
     * 从项目读取 Docker 配置。
     *
     * @param project 项目（可为 {@code null}）
     * @return 配置对象，永不为 {@code null}
     */
    public static DockerProjectSettings of(MsServerProject project) {
        Map<String, String> values = new LinkedHashMap<>();
        String raw = project == null ? null : project.getAppConfig();
        if (raw != null && !raw.isBlank()) {
            try {
                Object parsed = JSON.readValue(raw, Object.class);
                if (parsed instanceof Map) {
                    ((Map<?, ?>) parsed).forEach((k, v) -> {
                        if (k != null && v != null) {
                            values.put(String.valueOf(k), v instanceof Collection || v.getClass().isArray()
                                    ? join(v) : String.valueOf(v));
                        }
                    });
                }
            } catch (Exception ignored) {
                // 不是 JSON 对象（Java 项目的环境变量数组）时按「未配置」处理
            }
        }
        return new DockerProjectSettings(values);
    }

    /**
     * 生成脚本占位符映射。
     *
     * @param project 项目（主入口即容器名，项目名用于兜底与镜像默认值）
     * @return 占位符到展开值的映射
     */
    public Map<String, String> placeholders(MsServerProject project) {
        String appName = safeName(project == null ? null : project.getProjectName());
        String rawEntry = project == null || project.getMainEntry() == null ? "" : project.getMainEntry().trim();
        String entry = rawEntry.isEmpty() ? "" : safeName(rawEntry);
        // 容器名：容器名配置 > 主入口（Docker 项目把主入口当容器名）> 项目名
        String container = value("container", entry.isEmpty() ? appName : entry);
        String image = value("image", container + LATEST);
        Map<String, String> out = new LinkedHashMap<>();
        out.put(ProjectScripts.PH_DK_DOCKERFILE, quote(value("dockerfile", DEFAULT_DOCKERFILE)));
        out.put(ProjectScripts.PH_DK_CONTEXT, quote(value("context", DEFAULT_CONTEXT)));
        out.put(ProjectScripts.PH_DK_IMAGE, image);
        out.put(ProjectScripts.PH_DK_CTR, container);
        out.put(ProjectScripts.PH_DK_PORTS, flags("-p ", items("ports")));
        out.put(ProjectScripts.PH_DK_ENV, flags("-e ", items("env")));
        out.put(ProjectScripts.PH_DK_VOLUMES, flags("-v ", items("volumes")));
        out.put(ProjectScripts.PH_DK_NETWORK, network());
        out.put(ProjectScripts.PH_DK_RESTART, value("restart", DEFAULT_RESTART));
        out.put(ProjectScripts.PH_DK_BUILDARGS, flags("--build-arg ", items("buildArgs")));
        out.put(ProjectScripts.PH_DK_CMD, sanitize(value("command", "")));
        return out;
    }

    /**
     * 把脚本里的 Docker 占位符替换成实际值（不修改其它占位符）。
     *
     * @param script  脚本（可为空）
     * @param project 项目
     * @return 替换后的脚本；入参为空时原样返回
     */
    public static String expand(String script, MsServerProject project) {
        if (script == null || !script.contains("__DK_")) {
            return script;
        }
        String out = script;
        for (Map.Entry<String, String> e : of(project).placeholders(project).entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * 取配置值并做白名单过滤。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 过滤后的值
     */
    private String value(String key, String defaultValue) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String cleaned = sanitize(raw.trim());
        return cleaned.isEmpty() ? defaultValue : cleaned;
    }

    /**
     * 取「多个值」配置：数组（已按空格拼接）或空白分隔。
     *
     * <p>除过滤字符外还按用途校验：端口/数据卷必须含 {@code :}，环境变量/构建参数必须含
     * {@code =}，且不接受以 {@code -} 开头的片段 —— 否则 {@code 8080:8080; rm -rf /}
     * 这类配置被切词后会变成一串 {@code -p rm -p -rf} 的垃圾参数，虽然不执行命令，
     * 但会把容器启动搞成难以理解的结果。</p>
     *
     * @param key 键
     * @return 拆分后的值列表
     */
    private String[] items(String key) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        boolean needColon = "ports".equals(key) || "volumes".equals(key);
        boolean needEquals = "env".equals(key) || "buildArgs".equals(key);
        String[] parts = raw.trim().split("[\\s,]+");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String part : parts) {
            String cleaned = sanitize(part);
            if (cleaned.isEmpty() || cleaned.startsWith("-")) {
                continue;
            }
            if (needColon && !cleaned.contains(":")) {
                continue;
            }
            if (needEquals && !cleaned.contains("=")) {
                continue;
            }
            out.add(cleaned);
        }
        return out.toArray(new String[0]);
    }

    /**
     * 把多个值展开成带前缀的参数串。
     *
     * @param prefix 命令前缀（如 {@code -p }）
     * @param items  值
     * @return 参数串；无值返回空串
     */
    private static String flags(String prefix, String[] items) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(prefix).append(item);
        }
        return sb.toString();
    }

    /**
     * 网络参数（值可能为空）。
     *
     * @return {@code --network x} 或空串
     */
    private String network() {
        String net = value("network", "");
        return net.isEmpty() ? "" : "--network " + net;
    }

    /**
     * 数组/集合转空格拼接字符串。
     *
     * @param raw 原始值
     * @return 拼接结果
     */
    private static String join(Object raw) {
        if (raw instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder();
            for (Object o : c) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(o);
            }
            return sb.toString();
        }
        if (raw.getClass().isArray()) {
            return join(java.util.Arrays.asList((Object[]) raw));
        }
        return String.valueOf(raw);
    }

    /**
     * 应用名归一：容器名/镜像名只允许小写字母、数字、{@code _ . -}。
     *
     * @param name 项目名
     * @return 归一名；为空时返回 {@code app}
     */
    private static String safeName(String name) {
        String base = name == null ? "" : name.trim().toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-') {
                sb.append(c);
            }
        }
        String out = sb.toString().replaceAll("^[.\\-]+", "");
        return out.isEmpty() ? "app" : out;
    }

    /**
     * 单值白名单：拒绝 shell 元字符，保留路径常用符号。
     *
     * @param raw 原始值
     * @return 过滤后的值
     */
    private static String sanitize(String raw) {
        return sanitize(raw, false);
    }

    /**
     * 路径类值加单引号，避免空格把参数拆开。
     *
     * @param raw 原始值
     * @return 加引号后的值
     */
    private static String quote(String raw) {
        String cleaned = sanitize(raw, true);
        return cleaned.isEmpty() ? "''" : "'" + cleaned + "'";
    }

    /**
     * 白名单过滤。
     *
     * @param raw       原始值
     * @param allowBlank 是否允许空格（路径可以含空格，参数值不允许）
     * @return 过滤后的值
     */
    private static String sanitize(String raw, boolean allowBlank) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == ':' || c == '/' || c == '\\' || c == '='
                    || c == '@' || c == ',' || c == '+' || c == '-';
            if (!ok && allowBlank && c == ' ') {
                ok = true;
            }
            if (ok) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
