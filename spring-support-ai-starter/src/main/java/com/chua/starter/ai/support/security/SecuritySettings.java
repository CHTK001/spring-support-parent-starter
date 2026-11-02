package com.chua.starter.ai.support.security;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全分组设置的短周期本地快照。
 *
 * <h2>为什么需要它</h2>
 * <p>{@link GlobalSettingReader} 的底层是 {@code @Cacheable} 的 Redis 缓存：每次
 * {@code getStr/getBool/getInt} 都是一次 Redis 往返。一条网关请求要过 6 个过滤器、
 * 读十几个字段，直接读设置表等于给每条请求挂上十几次 Redis GET —— 限流器本身很便宜，
 * 设置读取反而成了瓶颈。</p>
 *
 * <p>因此这里按字段做一个 <b>1 秒</b>的本地快照：改完设置页最多 1 秒生效（对安全开关
 * 而言完全够用），而每条请求的 Redis 往返被摊薄到接近 0。字段数量固定（十几条），
 * 缓存不会无界增长。</p>
 *
 * <h2>失败语义</h2>
 * <p>底层读取抛异常时返回调用方给的默认值，<b>不抛</b>：安全过滤器必须 fail-open，
 * Redis 抖动不能让网关整片 500。异常结果<b>不进缓存</b>，恢复后立刻生效。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class SecuritySettings {

    /**
     * 设置字段所属分组
     */
    public static final String GROUP = "security";

    /**
     * 本地快照有效期（毫秒）
     */
    private static final long TTL_MILLIS = 1_000L;

    /**
     * 底层设置读取器
     */
    private final GlobalSettingReader reader;

    /**
     * 字段值快照：key 为字段名
     */
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>(32);

    /**
     * 构造器注入。
     *
     * @param reader 全局设置读取器
     */
    public SecuritySettings(GlobalSettingReader reader) {
        this.reader = reader;
    }

    /**
     * 读取布尔值。
     *
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 布尔值
     */
    public boolean getBool(String field, boolean defaultValue) {
        String raw = getRaw(field);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String lower = raw.trim().toLowerCase();
        return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower);
    }

    /**
     * 读取整数值。
     *
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 整数值
     */
    public int getInt(String field, int defaultValue) {
        String raw = getRaw(field);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.debug("[SecuritySettings] 字段 {} 非整数: {}", field, raw);
            return defaultValue;
        }
    }

    /**
     * 读取字符串值。
     *
     * @param field 字段名
     * @return 字符串值，未配置返回 {@code null}
     */
    public String getStr(String field) {
        return getRaw(field);
    }

    /**
     * 命中快照则返回，否则读一次设置表。
     *
     * @param field 字段名
     * @return 原始值，读取失败返回 {@code null}（不缓存失败结果）
     */
    private String getRaw(String field) {
        long now = System.currentTimeMillis();
        Entry cached = cache.get(field);
        if (cached != null && cached.expireAt > now) {
            return cached.value;
        }
        try {
            String value = reader.getStr(GROUP, field);
            cache.put(field, new Entry(value, now + TTL_MILLIS));
            return value;
        } catch (Exception e) {
            log.warn("[SecuritySettings] 读取设置失败 field={}，沿用默认值: {}", field, e.getMessage());
            return cached == null ? null : cached.value;
        }
    }

    /**
     * 快照条目。
     */
    private static final class Entry {

        /**
         * 原始值（可为 null，表示该字段未配置）
         */
        private final String value;

        /**
         * 到期时间戳
         */
        private final long expireAt;

        private Entry(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}
