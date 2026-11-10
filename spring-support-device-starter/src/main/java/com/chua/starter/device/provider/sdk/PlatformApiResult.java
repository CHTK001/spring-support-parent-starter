package com.chua.starter.device.provider.sdk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次平台接口调用的结果。
 *
 * <p>门面把平台原始响应统一成这个结构：调用方拿到的是「成功与否 + 数据 + 原始 JSON」，
 * 不需要关心是官方 SDK、OpenAPI HTTP 还是哪个版本的信封格式。</p>
 *
 * @param code    接口编码
 * @param ok      是否成功
 * @param message 失败原因 / 平台提示
 * @param data    归一后的数据（列表或对象），失败时为空
 * @param raw     平台原始响应文本，排障用
 * @param elapsedMs 耗时（毫秒）
 * @since 4.0.0.51
 * @author CH
 */
public record PlatformApiResult(
        String code,
        boolean ok,
        String message,
        Object data,
        String raw,
        long elapsedMs
) {

    /**
     * 构造成功结果。
     *
     * @param code      接口编码
     * @param data      归一数据
     * @param raw       原始响应
     * @param elapsedMs 耗时
     * @return 结果
     */
    public static PlatformApiResult ok(String code, Object data, String raw, long elapsedMs) {
        return new PlatformApiResult(code, true, null, data, raw, elapsedMs);
    }

    /**
     * 构造失败结果。
     *
     * @param code      接口编码
     * @param message   失败原因
     * @param raw       原始响应
     * @param elapsedMs 耗时
     * @return 结果
     */
    public static PlatformApiResult fail(String code, String message, String raw, long elapsedMs) {
        return new PlatformApiResult(code, false, message, null, raw, elapsedMs);
    }

    /**
     * 转成可直接返回前端的 Map。
     *
     * @return 键值视图
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("ok", ok);
        map.put("message", message);
        map.put("data", data);
        map.put("elapsedMs", elapsedMs);
        return map;
    }

    /**
     * 数据是否为空列表（用于「接口通但没数据」与「失败」的区分）。
     *
     * @return true 表示成功但没有数据
     */
    public boolean empty() {
        if (!ok) {
            return false;
        }
        if (data == null) {
            return true;
        }
        return data instanceof List<?> list && list.isEmpty();
    }
}
