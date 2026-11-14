// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelCreditProvider;
import com.chua.starter.common.support.text.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenCode（Zen / Go）用量查询实现。
 *
 * <p>端点：{@code GET https://opencode.ai/zen/go/v1/usage}（{@code Authorization: Bearer <API key>}），
 * 回滚动 5 小时 / 每周 / 每月三个窗口的已用百分比：</p>
 *
 * <pre>
 * {"usage":{"rolling":{"status":"ok","percent":12,"resetsAt":"..."},
 *           "weekly":{"status":"ok","percent":34,"resetsAt":"..."},
 *           "monthly":{"status":"ok","percent":56,"resetsAt":"..."}}}
 * </pre>
 *
 * <p>⚠️ 这条路径<b>不在官方文档里</b>。官方文档（{@code /docs/console/go}、
 * {@code /docs/console/api/inference}）只写「当前用量去 Console 看」，并把每次响应的
 * {@code usage.opencode.allowance}（Go 专属，单位是套餐月额度的美元数）当作唯一公开读数，
 * 没有任何查询端点。本实现用的路径是探活确认存在的（假令牌 401，同前缀捏造路径 404），
 * 字段口径与 magpie 社区插件一致；上游改名就会 404，失败原因会显示在渠道详情里。</p>
 *
 * <p>⚠️ 别去找「余额」：Zen 的<b>钱包余额（USD）没有公开接口</b>——{@code /zen/v1/usage}、
 * {@code /zen/v1/balance}、{@code /zen/v1/credits}、{@code /inference/v1/credits}、
 * {@code /inference/v1/billing/balance} 用假令牌全部 404，余额只能从登录态控制台读。
 * 所以本实现的 {@code remain} 是<b>最紧窗口的剩余百分比</b>，没有绝对金额可用。</p>
 *
 * <p>两条分支：</p>
 * <ul>
 *   <li>凭据是匿名通道字面量 {@code public}（本系统免费车道的默认值）→ 上游对免费通道
 *       不开放任何用量端点（只有 429 {@code FreeUsageLimitError}），直接给可操作提示，
 *       不发请求；</li>
 *   <li>凭据是真实 Zen / Go API key → 照常查；403 表示该 key 没有 Go 订阅。</li>
 * </ul>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi("opencode")
public class OpencodeChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "opencode";

    /**
     * Go 用量端点（只有 Go 套餐的 key 能读；未在官方文档记载，按社区插件口径 + 假令牌探活确认）
     */
    static final String USAGE_URL = "https://opencode.ai/zen/go/v1/usage";

    /**
     * 匿名免费通道字面量 key（与鉴权管理器同口径）
     */
    static final String ANONYMOUS_KEY = "public";

    /**
     * 满配额百分比
     */
    static final long FULL_PERCENT = 100L;

    /**
     * 窗口展示名（键 → 中文名）
     */
    static final String[][] WINDOWS = {
            {"rolling", "滚动 5 小时"},
            {"weekly", "每周"},
            {"monthly", "每月"},
    };

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String creditUnit() {
        return "%";
    }

    /**
     * {@inheritDoc}
     *
     * <p>永不抛异常：缺凭据、匿名通道、401/403、结构异常都写 {@code error} 返回。</p>
     */
    @Override
    public Map<String, Object> credit(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(8);
        String apiKey = readString(credentials, "access_token");
        if (isBlank(apiKey)) {
            result.put("error", "凭据缺少 access_token（Zen API key）");
            return result;
        }
        if (ANONYMOUS_KEY.equals(apiKey.trim())) {
            result.put("error", "匿名免费通道没有用量接口（Zen 不开放），粘贴真实 Zen API key 后可读 Go 套餐用量");
            return result;
        }
        try {
            Map<String, Object> payload = parseJson(
                    ChannelHttp.getJson(USAGE_URL, authHeaders(apiKey)));
            Object usageRaw = payload.get("usage");
            if (!(usageRaw instanceof Map<?, ?> raw)) {
                result.put("error", "上游未返回 usage（该 key 可能没有 OpenCode Go 套餐）");
                return result;
            }
            Map<String, Object> usage = asMap(raw);
            List<Map<String, Object>> packages = packagesOf(usage);
            if (packages.isEmpty()) {
                result.put("error", "上游 usage 未包含可解析的窗口百分比");
                return result;
            }
            long remain = FULL_PERCENT;
            for (Map<String, Object> row : packages) {
                remain = Math.min(remain, toLong(row.get("remain")));
            }
            result.put("remain", Math.max(remain, 0L));
            result.put("total", FULL_PERCENT);
            result.put("unit", "%");
            result.put("updatedAt", System.currentTimeMillis());
            result.put("packages", packages);
            return result;
        } catch (Exception e) {
            result.put("error", failureOf(e));
            return result;
        }
    }

    /**
     * 明细行：每个窗口一行，{@code remain = 100 - percent}。
     *
     * @param usage 用量节点
     * @return 明细行
     */
    private static List<Map<String, Object>> packagesOf(Map<String, Object> usage) {
        List<Map<String, Object>> rows = new ArrayList<>(WINDOWS.length);
        for (String[] window : WINDOWS) {
            if (!(usage.get(window[0]) instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> node = asMap(raw);
            Long used = toLongOrNull(node.get("percent"));
            if (used == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(3);
            row.put("name", window[1]);
            row.put("remain", Math.max(FULL_PERCENT - Math.min(used, FULL_PERCENT), 0L));
            row.put("size", FULL_PERCENT);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 失败归类：{@code -> 401} 是 key 失效，{@code -> 403} 是没有 Go 套餐，
     * 其余给通用文案（上游报错原文不进界面）。
     *
     * @param e 异常
     * @return 展示文案
     */
    private static String failureOf(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("-> 401")) {
            return "Zen API key 无效或已过期，请重新粘贴";
        }
        if (message.contains("-> 403")) {
            return "该 key 没有 OpenCode Go 套餐（Zen 免费通道不开放用量接口）";
        }
        return "用量查询失败，请稍后重试";
    }

    /**
     * 鉴权头。
     *
     * @param apiKey API key
     * @return 请求头
     */
    private static Map<String, String> authHeaders(String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>(4);
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + apiKey.trim());
        return headers;
    }

    /**
     * Map 键归一化。
     *
     * @param raw 原始 Map
     * @return 字符串键 Map
     */
    private static Map<String, Object> asMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>(raw.size());
        raw.forEach((k, v) -> {
            if (k != null) {
                out.put(String.valueOf(k), v);
            }
        });
        return out;
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
    private static Long toLongOrNull(Object value) {
        if (value instanceof Number number) {
            return Math.round(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Math.round(Double.parseDouble(text.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 数字归一（缺失按 0）。
     *
     * @param value 原始值
     * @return 长整数；不可解析返回 {@code 0}
     */
    private static long toLong(Object value) {
        Long number = toLongOrNull(value);
        return number == null ? 0L : number;
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
