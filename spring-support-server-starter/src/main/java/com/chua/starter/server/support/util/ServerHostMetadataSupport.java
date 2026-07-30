package com.chua.starter.server.support.util;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerMetricsTaskSettingsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 统一解析服务器 metadataJson 中的扩展配置，避免各业务重复手写 JSON 读取逻辑。
 */
public final class ServerHostMetadataSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ServerHostMetadataSupport() {
    }

    /**
     * 解析指标采集 provider；显式配置优先，未配置时回落到服务器接入协议。
     */
    public static String resolveMetricsProvider(ServerHost host) {
        JsonNode metadata = parseMetadata(host);
        String provider = firstText(metadata,
                "metricsProvider",
                "metrics.provider",
                "metrics.collector",
                "metrics.providerType");
        if (StringUtils.hasText(provider)) {
            return provider.trim().toLowerCase(Locale.ROOT);
        }
        if (StringUtils.hasText(resolvePrometheusBaseUrl(host))) {
            return "prometheus";
        }
        if (host == null || !StringUtils.hasText(host.getServerType())) {
            return "local";
        }
        return host.getServerType().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断当前主机是否启用了 Prometheus 采集模式。
     */
    public static boolean isPrometheusMetrics(ServerHost host) {
        return "prometheus".equalsIgnoreCase(resolveMetricsProvider(host));
    }

    /**
     * 读取 Prometheus 基础地址，例如 `http://127.0.0.1:9090`。
     */
    public static String resolvePrometheusBaseUrl(ServerHost host) {
        JsonNode metadata = parseMetadata(host);
        return trimTrailingSlash(firstText(metadata,
                "prometheus.baseUrl",
                "prometheus.url",
                "prometheusBaseUrl",
                "metrics.prometheus.baseUrl",
                "metrics.prometheus.url",
                "metrics.prometheusBaseUrl"));
    }

    /**
     * 读取 Prometheus 实例标识；未配置时优先回落到服务器 IP。
     */
    public static String resolvePrometheusInstance(ServerHost host) {
        JsonNode metadata = parseMetadata(host);
        String instance = firstText(metadata,
                "prometheus.instance",
                "prometheus.target",
                "prometheusInstance",
                "metrics.prometheus.instance",
                "metrics.prometheus.target");
        if (StringUtils.hasText(instance)) {
            return instance.trim();
        }
        return host == null ? null : host.getHost();
    }

    /**
     * 读取 Prometheus 历史查询步长，默认 60 秒。
     */
    public static int resolvePrometheusStepSeconds(ServerHost host, int defaultValue) {
        JsonNode metadata = parseMetadata(host);
        Integer step = firstInteger(metadata,
                "prometheus.stepSeconds",
                "prometheus.historyStepSeconds",
                "prometheusStepSeconds",
                "metrics.prometheus.stepSeconds",
                "metrics.prometheus.historyStepSeconds");
        return step == null || step <= 0 ? defaultValue : step;
    }

    /**
     * 读取 Prometheus 瞬时查询窗口，默认 120 秒。
     */
    public static int resolvePrometheusLookbackSeconds(ServerHost host, int defaultValue) {
        JsonNode metadata = parseMetadata(host);
        Integer lookback = firstInteger(metadata,
                "prometheus.lookbackSeconds",
                "prometheus.rangeSeconds",
                "prometheusLookbackSeconds",
                "metrics.prometheus.lookbackSeconds");
        return lookback == null || lookback <= 0 ? defaultValue : lookback;
    }

    /**
     * 判断 Prometheus 查询是否需要经由服务器 SPI 远程执行。
     *
     * <p>显式配置优先；未配置时，若远程主机把 Prometheus 绑定在 `127.0.0.1/localhost`
     * 这类回环地址，则自动改走服务器协议自身的命令执行能力。
     */
    public static boolean resolvePrometheusViaSpi(ServerHost host) {
        JsonNode metadata = parseMetadata(host);
        Boolean viaSpi = firstBoolean(metadata,
                "prometheus.viaSpi",
                "prometheus.remoteViaSpi",
                "prometheus.useSpi",
                "metrics.prometheus.viaSpi",
                "metrics.prometheus.useSpi");
        if (viaSpi != null) {
            return viaSpi;
        }
        if (host == null
                || !StringUtils.hasText(host.getServerType())
                || "LOCAL".equalsIgnoreCase(host.getServerType())) {
            return false;
        }
        String baseUrl = resolvePrometheusBaseUrl(host);
        if (!StringUtils.hasText(baseUrl)) {
            return false;
        }
        try {
            String hostname = URI.create(baseUrl).getHost();
            return isLoopbackHost(hostname);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 判断当前主机是否继承全局指标采集配置。
     */
    public static boolean isMetricsTaskInheritGlobal(ServerHost host) {
        JsonNode metadata = parseMetadata(host);
        Boolean inheritGlobal = firstBoolean(metadata,
                "metricsTask.inheritGlobal",
                "metricsTaskInheritGlobal",
                "metrics.inheritGlobal");
        return inheritGlobal == null || inheritGlobal;
    }

    /**
     * 解析主机级指标采集开关；未单独配置时回落到全局值。
     */
    public static boolean resolveMetricsEnabled(ServerHost host, boolean defaultValue) {
        if (isMetricsTaskInheritGlobal(host)) {
            return defaultValue;
        }
        JsonNode metadata = parseMetadata(host);
        Boolean value = firstBoolean(metadata,
                "metricsTask.enabled",
                "metricsTaskEnabled",
                "metrics.enabled");
        return value == null ? defaultValue : value;
    }

    /**
     * 解析主机级采集间隔；未单独配置时回落到全局值。
     */
    public static long resolveMetricsRefreshIntervalMs(ServerHost host, long defaultValue) {
        if (isMetricsTaskInheritGlobal(host)) {
            return defaultValue;
        }
        JsonNode metadata = parseMetadata(host);
        Long value = firstLong(metadata,
                "metricsTask.refreshIntervalMs",
                "metricsTaskRefreshIntervalMs",
                "metrics.refreshIntervalMs");
        return value == null || value <= 0L ? defaultValue : value;
    }

    /**
     * 解析主机级采集超时；未单独配置时回落到全局值。
     */
    public static int resolveMetricsTimeoutMs(ServerHost host, int defaultValue) {
        if (isMetricsTaskInheritGlobal(host)) {
            return defaultValue;
        }
        JsonNode metadata = parseMetadata(host);
        Integer value = firstInteger(metadata,
                "metricsTask.timeoutMs",
                "metricsTaskTimeoutMs",
                "metrics.timeoutMs");
        return value == null || value <= 0 ? defaultValue : value;
    }

    /**
     * 解析主机级缓存开关；未单独配置时回落到全局值。
     */
    public static boolean resolveMetricsCacheEnabled(ServerHost host, boolean defaultValue) {
        if (isMetricsTaskInheritGlobal(host)) {
            return defaultValue;
        }
        JsonNode metadata = parseMetadata(host);
        Boolean value = firstBoolean(metadata,
                "metricsTask.cacheEnabled",
                "metricsTaskCacheEnabled",
                "metrics.cacheEnabled");
        return value == null ? defaultValue : value;
    }

    /**
     * 解析主机级缓存 TTL；未单独配置时回落到全局值。
     */
    public static long resolveMetricsCacheTtlSeconds(ServerHost host, long defaultValue) {
        if (isMetricsTaskInheritGlobal(host)) {
            return defaultValue;
        }
        JsonNode metadata = parseMetadata(host);
        Long value = firstLong(metadata,
                "metricsTask.cacheTtlSeconds",
                "metricsTaskCacheTtlSeconds",
                "metrics.cacheTtlSeconds");
        return value == null || value <= 0L ? defaultValue : value;
    }

    /**
     * 把单机指标采集配置写回 metadataJson。
     */
    public static String applyMetricsTaskSettings(ServerHost host, ServerMetricsTaskSettingsRequest request) {
        ObjectNode metadata = parseMetadataObject(host);
        if (request == null) {
            return metadata.isEmpty() ? null : metadata.toString();
        }
        boolean inheritGlobal = request.getInheritGlobal() == null || request.getInheritGlobal();
        if (inheritGlobal) {
            metadata.remove("metricsTask");
            metadata.remove("metricsTaskInheritGlobal");
            return metadata.isEmpty() ? null : metadata.toString();
        }
        ObjectNode metricsTask = metadata.with("metricsTask");
        metricsTask.put("inheritGlobal", false);
        putBoolean(metricsTask, "enabled", request.getEnabled());
        putLong(metricsTask, "refreshIntervalMs", request.getRefreshIntervalMs());
        putInteger(metricsTask, "timeoutMs", request.getTimeoutMs());
        putBoolean(metricsTask, "cacheEnabled", request.getCacheEnabled());
        putLong(metricsTask, "cacheTtlSeconds", request.getCacheTtlSeconds());
        return metadata.toString();
    }

    /**
     * 解析 metadataJson 为 JsonNode，解析失败时返回空节点。
     */
    public static JsonNode parseMetadata(ServerHost host) {
        return parseMetadataObject(host);
    }

    /**
     * 解析 metadataJson 为 ObjectNode，便于后续原地修改。
     */
    public static ObjectNode parseMetadataObject(ServerHost host) {
        if (host == null || !StringUtils.hasText(host.getMetadataJson())) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(host.getMetadataJson());
            return node instanceof ObjectNode objectNode ? objectNode : OBJECT_MAPPER.createObjectNode();
        } catch (Exception ignored) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    /**
     * 在多个候选 key 中读取首个非空文本值。
     */
    private static String firstText(JsonNode metadata, String... candidates) {
        if (metadata == null || candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            JsonNode node = readNode(metadata, candidate);
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                String value = node.asText(null);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 在多个候选 key 中读取首个有效整数值。
     */
    private static Integer firstInteger(JsonNode metadata, String... candidates) {
        if (metadata == null || candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            JsonNode node = readNode(metadata, candidate);
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                if (node.isNumber()) {
                    return node.asInt();
                }
                String value = node.asText(null);
                if (StringUtils.hasText(value)) {
                    try {
                        return Integer.parseInt(value.trim());
                    } catch (NumberFormatException ignored) {
                        // continue
                    }
                }
            }
        }
        return null;
    }

    /**
     * 在多个候选 key 中读取首个有效长整型值。
     */
    private static Long firstLong(JsonNode metadata, String... candidates) {
        if (metadata == null || candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            JsonNode node = readNode(metadata, candidate);
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                if (node.isNumber()) {
                    return node.asLong();
                }
                String value = node.asText(null);
                if (StringUtils.hasText(value)) {
                    try {
                        return Long.parseLong(value.trim());
                    } catch (NumberFormatException ignored) {
                        // continue
                    }
                }
            }
        }
        return null;
    }

    /**
     * 在多个候选 key 中读取首个有效布尔值。
     */
    private static Boolean firstBoolean(JsonNode metadata, String... candidates) {
        if (metadata == null || candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            JsonNode node = readNode(metadata, candidate);
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            String value = node.asText(null);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if ("true".equalsIgnoreCase(value) || "1".equals(value.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(value) || "0".equals(value.trim())) {
                return false;
            }
        }
        return null;
    }

    /**
     * 支持点路径写法读取嵌套节点，如 `prometheus.baseUrl`。
     */
    private static JsonNode readNode(JsonNode metadata, String path) {
        if (metadata == null || !StringUtils.hasText(path)) {
            return null;
        }
        JsonNode current = metadata;
        for (String part : path.split("\\.")) {
            current = current == null ? null : current.path(part);
        }
        return current;
    }

    /**
     * 条件写入布尔值；空值时移除字段，避免残留无效覆盖。
     */
    private static void putBoolean(ObjectNode node, String key, Boolean value) {
        if (value == null) {
            node.remove(key);
            return;
        }
        node.put(key, value);
    }

    /**
     * 条件写入整数值；空值时移除字段，避免残留无效覆盖。
     */
    private static void putInteger(ObjectNode node, String key, Integer value) {
        if (value == null) {
            node.remove(key);
            return;
        }
        node.put(key, value);
    }

    /**
     * 条件写入长整型值；空值时移除字段，避免残留无效覆盖。
     */
    private static void putLong(ObjectNode node, String key, Long value) {
        if (value == null) {
            node.remove(key);
            return;
        }
        node.put(key, value);
    }

    /**
     * 去掉 URL 尾部斜杠，避免后续拼接重复。
     */
    private static String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 判断主机名是否属于本机回环地址。
     */
    private static boolean isLoopbackHost(String hostname) {
        if (!StringUtils.hasText(hostname)) {
            return false;
        }
        String value = hostname.trim().toLowerCase(Locale.ROOT);
        return "127.0.0.1".equals(value)
                || "localhost".equals(value)
                || "::1".equals(value)
                || "0:0:0:0:0:0:0:1".equals(value);
    }
}
