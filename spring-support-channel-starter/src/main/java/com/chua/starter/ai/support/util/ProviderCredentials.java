package com.chua.starter.ai.support.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 提供者 凭证读取工具。
 *
 * <p>凭证以 JSON 对象存于 {@code sys_ai_provider.sys_ai_provider_credentials}，历史上写入侧
 * 出现过 {@code api_key}（单键字符串）、{@code api_keys}（数组）、{@code apiKey}（驼峰标量）
 * 三种形态，管理接口按三种形态都能读出密钥，而对话/机器人/智能体链路各自实现时只认其中一部分，
 * 导致仅写了 {@code api_key} 的 提供者 在对话时被判为「无密钥」（openai-java 直接抛
 * At least one credential source must be specified）。全部出口统一走本类，避免再次分叉。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ProviderCredentials {

    /**
     * 多密钥数组键
     */
    private static final String KEY_API_KEYS = "api_keys";

    /**
     * 单密钥蛇形键
     */
    private static final String KEY_API_KEY = "api_key";

    /**
     * 单密钥驼峰键
     */
    private static final String KEY_API_KEY_CAMEL = "apiKey";

    /**
     * 工具类不允许实例化
     */
    private ProviderCredentials() {
    }

    /**
     * 收集凭证中的全部密钥（去重、保持声明顺序）。
     *
     * @param credentials 提供者 凭证，可为空
     * @return 密钥列表，无密钥时返回空列表
     */
    public static List<String> apiKeys(Map<String, Object> credentials) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (credentials == null) {
            return new ArrayList<>(1);
        }
        addAll(keys, credentials.get(KEY_API_KEYS));
        addAll(keys, credentials.get(KEY_API_KEY));
        addAll(keys, credentials.get(KEY_API_KEY_CAMEL));
        return new ArrayList<>(keys);
    }

    /**
     * 取凭证中的第一个密钥。
     *
     * @param credentials 提供者 凭证，可为空
     * @return 第一个密钥，无密钥时返回 null
     */
    public static String firstApiKey(Map<String, Object> credentials) {
        List<String> keys = apiKeys(credentials);
        return keys.isEmpty() ? null : keys.getFirst();
    }

    /**
     * 把一个键下的密钥值并入结果：支持数组形态，也支持逗号/空白分隔的单串形态。
     *
     * @param keys  结果集合
     * @param value 原始值
     */
    private static void addAll(LinkedHashSet<String> keys, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                add(keys, item);
            }
            return;
        }
        for (String part : value.toString().split("[,\\s]+")) {
            add(keys, part);
        }
    }

    /**
     * 并入单个非空密钥。
     *
     * @param keys  结果集合
     * @param value 原始值
     */
    private static void add(LinkedHashSet<String> keys, Object value) {
        if (value == null) {
            return;
        }
        String key = value.toString().trim();
        if (!key.isEmpty()) {
            keys.add(key);
        }
    }
}
