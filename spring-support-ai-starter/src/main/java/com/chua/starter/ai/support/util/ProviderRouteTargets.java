package com.chua.starter.ai.support.util;

import com.chua.common.support.ai.chat.route.RouteTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商路由表解析工具。
 *
 * <p>免费渠道等路由型 ChatClient 需要在「一个供应商」下按模型切换上游地址与凭据，
 * 路由表统一存放在供应商行的 {@code extra.routes} 中，形如：</p>
 * <pre>{@code
 * {
 *   "routes": {
 *     "glm-4.7": { "provider": "openai", "base_url": "https://...", "api_key": "sk-..." },
 *     "kilo":    { "provider": "kilo",   "base_url": "https://api.kilo.ai/api/gateway" },
 *     "default": { "provider": "openai", "base_url": "https://free.example.com/v1" }
 *   }
 * }
 * }</pre>
 *
 * <p>键可以是完整模型名、上游前缀（供 {@code 前缀/模型} 写法命中）或 {@code default}；
 * 字段同时兼容下划线与驼峰两种写法。未给出 {@code api_key} 时由客户端回落到供应商的全局 key。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ProviderRouteTargets {

    /**
     * 免费渠道的平台标识
     */
    public static final String FREE_CHANNEL_PLATFORM = "free-channel";

    /**
     * 免费渠道的平台别名
     */
    public static final String FREE_CHANNEL_ALIAS = "free";

    /**
     * 路由组供应商的平台标识。
     *
     * <p>路由组不是真实上游：它的「模型」来自已添加的模型库，会话选中其中任一模型时
     * 由 {@code RouterGroupChatClient} 按容器策略（智能 / 按顺序 / 用完再换 …）选出
     * 真实成员并转发。因此路由组本身不建普通 ChatClient，也没有上游地址与凭证。</p>
     */
    public static final String ROUTER_GROUP_PLATFORM = "router-group";

    /**
     * 是否为路由组平台
     *
     * @param platform 供应商平台标识
     * @return 路由组返回 {@code true}
     */
    public static boolean isRouterGroup(String platform) {
        if (platform == null) {
            return false;
        }
        return ROUTER_GROUP_PLATFORM.equalsIgnoreCase(platform.trim());
    }

    /**
     * extra 中的路由表键名
     */
    private static final String ROUTES_KEY = "routes";

    private ProviderRouteTargets() {
    }

    /**
     * 从供应商 extra 解析路由表
     *
     * @param extra 供应商扩展配置
     * @return 路由表；未配置或解析为空时返回 {@code null}
     */
    public static Map<String, RouteTarget> fromExtra(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        Object raw = extra.get(ROUTES_KEY);
        if (!(raw instanceof Map<?, ?> rawRoutes) || rawRoutes.isEmpty()) {
            return null;
        }
        Map<String, RouteTarget> routes = new LinkedHashMap<>(rawRoutes.size());
        for (Map.Entry<?, ?> entry : rawRoutes.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            RouteTarget target = toTarget(entry.getValue());
            if (target != null) {
                String key = String.valueOf(entry.getKey()).trim();
                if (!key.isEmpty()) {
                    routes.put(key, target);
                }
            }
        }
        return routes.isEmpty() ? null : routes;
    }

    /**
     * 是否为免费渠道平台
     *
     * @param platform 供应商平台标识
     * @return 免费渠道（含别名）返回 {@code true}
     */
    public static boolean isFreeChannel(String platform) {
        if (platform == null) {
            return false;
        }
        String value = platform.trim();
        return FREE_CHANNEL_PLATFORM.equalsIgnoreCase(value)
                || FREE_CHANNEL_ALIAS.equalsIgnoreCase(value);
    }

    /**
     * 把单个路由节点转成路由目标
     *
     * @param value 路由节点（Map）
     * @return 路由目标；节点不是 Map 时返回 {@code null}
     */
    private static RouteTarget toTarget(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> node = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (k != null) {
                node.put(String.valueOf(k), v);
            }
        });
        RouteTarget target = RouteTarget.builder()
                .provider(text(node, "provider", "protocol"))
                .baseUrl(text(node, "base_url", "baseUrl", "url", "endpoint"))
                .apiKey(text(node, "api_key", "apiKey"))
                .model(text(node, "model", "upstream_model", "upstreamModel"))
                .authScheme(text(node, "auth_scheme", "authScheme"))
                .authHeader(text(node, "auth_header", "authHeader"))
                .authPrefix(text(node, "auth_prefix", "authPrefix"))
                .apiKeys(stringList(node, "api_keys", "apiKeys", "keys"))
                .autoSwitchOnRateLimit(boolNode(node, "auto_switch_on_rate_limit", "autoSwitchOnRateLimit"))
                .loadStrategy(text(node, "load_strategy", "loadStrategy"))
                .build();
        if (!target.available()) {
            return null;
        }
        return target;
    }

    /**
     * 读取节点中的字符串列表字段（如 {@code api_keys}）。
     *
     * @param node 路由节点
     * @param keys 候选字段名（按优先级）
     * @return 列表；无有效项返回 {@code null}
     */
    private static List<String> stringList(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            Object value = node.get(key);
            if (value instanceof List<?> list && !list.isEmpty()) {
                List<String> result = new java.util.ArrayList<>(list.size());
                for (Object item : list) {
                    if (item != null && !String.valueOf(item).isBlank()) {
                        result.add(String.valueOf(item));
                    }
                }
                return result.isEmpty() ? null : result;
            }
            if (value instanceof String s && !s.isBlank()) {
                // 兼容「逗号分隔」写法
                String[] parts = s.split("\\s*,\\s*");
                if (parts.length > 0) {
                    return new java.util.ArrayList<>(java.util.List.of(parts));
                }
            }
        }
        return null;
    }

    /**
     * 读取节点中的布尔字段；缺失返回 {@code null}（由目标默认 {@code true}）。
     * @param Map<String 参数
     * @param keys 参数
     * @param node 参数
     * @return 返回值
    */
    private static Boolean boolNode(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            if (node.containsKey(key)) {
                Object value = node.get(key);
                if (value instanceof Boolean b) {
                    return b;
                }
                if (value instanceof String s) {
                    return !"false".equalsIgnoreCase(s.trim());
                }
            }
        }
        return null;
    }

    /**
     * 读取节点中的字符串字段
     *
     * @param node 路由节点
     * @param keys 候选字段名（按优先级）
     * @return 字段值；全部缺失时返回 {@code null}
     */
    private static String text(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            if (node.containsKey(key)) {
                Object value = node.get(key);
                return value == null ? null : String.valueOf(value);
            }
        }
        return null;
    }
}