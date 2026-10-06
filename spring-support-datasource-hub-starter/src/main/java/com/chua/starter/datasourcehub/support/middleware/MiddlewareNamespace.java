package com.chua.starter.datasourcehub.support.middleware;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中间件管理侧的通用小工具：命名空间归一与时间渲染。
 *
 * <p>Nacos/ZooKeeper 等中间件的「公有命名空间」在协议里是空串，而界面上统一显示为
 * {@code public}，因此出入参都要经过这里做一次归一，避免把 {@code public} 当真实 tenant 发出去。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class MiddlewareNamespace {

    /**
     * 公有命名空间在界面上的标识
     */
    public static final String PUBLIC = "public";

    /**
     * 展示给使用者的时间格式
     */
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MiddlewareNamespace() {
    }

    /**
     * 界面值 → 协议值。
     *
     * @param namespace 界面传来的命名空间标识
     * @return Nacos 的 tenant／ZooKeeper 的 namespace，公有空间为空串
     */
    public static String toProtocol(String namespace) {
        String value = namespace == null ? "" : namespace.trim();
        return PUBLIC.equalsIgnoreCase(value) ? "" : value;
    }

    /**
     * 协议值 → 界面值。
     *
     * @param namespace 中间件返回的命名空间标识
     * @return 非空原值，空串归一为 {@code public}
     */
    public static String toDisplay(String namespace) {
        String value = namespace == null ? "" : namespace.trim();
        return value.isEmpty() ? PUBLIC : value;
    }

    /**
     * 毫秒时间戳渲染成可读时间，按 JVM 默认时区。
     *
     * @param epochMillis 毫秒时间戳，可为 null
     * @return 可读时间，入参为 null 时返回 null
     */
    public static String format(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0L) {
            return null;
        }
        return DISPLAY.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    /**
     * 构造有序查询参数表，省略空值键。
     *
     * @param pairs 键值交替数组
     * @return 查询参数
     */
    public static Map<String, String> query(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String value = pairs[i + 1];
            if (value != null) {
                map.put(pairs[i], value);
            }
        }
        return map;
    }
}
