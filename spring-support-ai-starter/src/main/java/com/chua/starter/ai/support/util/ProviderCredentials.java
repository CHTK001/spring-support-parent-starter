package com.chua.starter.ai.support.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 提供者 凭证读取工具。
 *
 * <p>凭证以 JSON 对象存于 {@code sys_account_channel.sys_account_channel_credentials}，历史上写入侧
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
     * OAuth 渠道的令牌键。
     *
     * <p>OpenCode / Gemini / Trae 这类渠道把粘贴的 key 或登录令牌统一存成
     * {@code access_token}（见账号模块各 {@code *AccountChannelAuthManager}），
     * 只认 {@code api_key} 会让「账号已绑定、模型已启用」的渠道在对话时被判成无密钥。</p>
     */
    private static final String KEY_ACCESS_TOKEN = "access_token";

    /**
     * 签名密钥蛇形键
     */
    private static final String KEY_SECRET_KEY = "secret_key";

    /**
     * 签名密钥驼峰键
     */
    private static final String KEY_SECRET_KEY_CAMEL = "secretKey";

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
        addAll(keys, credentials.get(KEY_ACCESS_TOKEN));
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
     * 取凭证中的 SecretKey（签名密钥）。
     *
     * <p>部分供应商（如 LiblibAI）除 AccessKey 外还需要独立的 SecretKey 做请求签名。
     * 该键<b>不并入</b> {@link #apiKeys(Map)}：apiKeys 面向「可轮换的 API Key 列表」，
     * SecretKey 是签名密钥，混进去会导致限流换 Key 逻辑误把签名密钥当普通 Key 轮换。</p>
     *
     * @param credentials 提供者 凭证，可为空
     * @return SecretKey，无则返回 null
     */
    public static String secretKey(Map<String, Object> credentials) {
        if (credentials == null) {
            return null;
        }
        Object value = credentials.containsKey(KEY_SECRET_KEY)
                ? credentials.get(KEY_SECRET_KEY) : credentials.get(KEY_SECRET_KEY_CAMEL);
        if (value == null) {
            return null;
        }
        String key = value.toString().trim();
        return key.isEmpty() ? null : key;
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
