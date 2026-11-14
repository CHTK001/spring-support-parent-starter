// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelCreditProvider;
import com.chua.starter.common.support.text.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EAC 协付渠道的可用量查询实现。
 *
 * <p>EAC 没有「账号余额」这一概念：它是仓库维护者部署的<b>共享协付池</b>，用量以
 * 并发槽计。网关自己暴露只读端点
 * {@code GET https://ourfreemodel.beichenapi.com/eac/pool}（<b>无需鉴权</b>，实测 200）：</p>
 *
 * <pre>
 * {"ok":true,"inflight":58,"queued":0,"capacity":2000,"utilization":2.9,
 *  "backend":"memory","scope":"single-instance","ts":1794622262441,"dynamic":{...}}
 * </pre>
 *
 * <p>本实现把「剩余并发槽 = capacity - inflight」作为 {@code remain}，{@code capacity}
 * 作为 {@code total}，与官方插件设置页「EAC 模型（鱼缸水位 = 协付池压力）」同一口径。
 * 因此这里的数字是<b>全池</b>的，不是本账号的——协付渠道本来就只有池子这一个量。</p>
 *
 * <p>已探过但<b>不存在</b>的路径（404）：{@code /auth/me}、{@code /api/usage}、
 * {@code /api/quota}、{@code /usage}、{@code /quota}、{@code /v1/usage}、
 * {@code /v1/quota}、{@code /resource}；{@code /v1/models} 需 HMAC 签名（401
 * {@code request signature rejected}），不能拿来读额度。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi("eac")
public class EacChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "eac";

    /**
     * 协付池状态端点（公开只读）
     */
    static final String POOL_URL = "https://ourfreemodel.beichenapi.com/eac/pool";

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String creditUnit() {
        return "slot";
    }

    /**
     * {@inheritDoc}
     *
     * <p>永不抛异常：网络失败、结构异常都写 {@code error} 返回。</p>
     */
    @Override
    public Map<String, Object> credit(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(8);
        try {
            Map<String, Object> pool = parseJson(ChannelHttp.getJson(POOL_URL, null));
            if (pool.isEmpty()) {
                result.put("error", "协付池端点返回空响应");
                return result;
            }
            Long capacity = toLong(pool.get("capacity"));
            Long inflight = toLong(pool.get("inflight"));
            if (capacity == null || inflight == null) {
                result.put("error", "协付池端点未返回 capacity / inflight");
                return result;
            }
            long free = Math.max(capacity - inflight, 0L);
            result.put("remain", free);
            result.put("total", Math.max(capacity, 0L));
            result.put("unit", "slot");
            result.put("updatedAt", updatedAtOf(pool));
            String backend = readString(pool, "backend");
            if (!isBlank(backend)) {
                result.put("plan", "协付池 · " + backend);
            }
            result.put("packages", packagesOf(free, capacity, inflight, pool.get("queued")));
            return result;
        } catch (Exception e) {
            // 上游原文（URL / HTML）不进界面
            result.put("error", "协付池状态查询失败，请稍后重试");
            return result;
        }
    }

    /**
     * 组装明细行：空闲槽、占用、排队各一行，让页面能直接看出池子是被占满还是有人在排队。
     *
     * @param free     空闲并发槽
     * @param capacity 总并发槽
     * @param inflight 占用中的并发数
     * @param queued   排队数（可空）
     * @return 明细行
     */
    private static java.util.List<Map<String, Object>> packagesOf(long free, long capacity, long inflight,
                                                                  Object queued) {
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>(3);
        rows.add(row("空闲并发槽", free, capacity));
        rows.add(row("占用中", inflight, capacity));
        Long waiting = toLong(queued);
        if (waiting != null && waiting > 0) {
            // 排队没有上限，size 缺省即「上游未提供」
            Map<String, Object> row = new LinkedHashMap<>(3);
            row.put("name", "排队中");
            row.put("remain", waiting);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 明细行。
     *
     * @param name   名称
     * @param remain 剩余
     * @param size   总量
     * @return 明细行
     */
    private static Map<String, Object> row(String name, long remain, long size) {
        Map<String, Object> row = new LinkedHashMap<>(3);
        row.put("name", name);
        row.put("remain", remain);
        row.put("size", size);
        return row;
    }

    /**
     * 读数时刻：优先用上游 {@code ts}（毫秒），缺失时用本机时钟。
     *
     * @param pool 池状态
     * @return 毫秒时间戳
     */
    private static long updatedAtOf(Map<String, Object> pool) {
        Long ts = toLong(pool.get("ts"));
        return ts == null || ts <= 0L ? System.currentTimeMillis() : ts;
    }

    /**
     * 解析 JSON（失败返回空 Map）。
     *
     * @param text JSON 文本
     * @return 解析结果
     */
    private static Map<String, Object> parseJson(String text) {
        if (isBlank(text)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = Json.getJsonObject(text);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 数字归一。
     *
     * @param value 原始值
     * @return 长整数；不可解析返回 {@code null}
     */
    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 读取非空字符串。
     *
     * @param source 源 Map
     * @param key    键
     * @return 值；空串返回 {@code null}
     */
    private static String readString(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    /**
     * 判断空串。
     *
     * @param value 字符串
     * @return 空或 null 返回 {@code true}
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
