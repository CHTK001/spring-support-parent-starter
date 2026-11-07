// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelCreditProvider;
import com.chua.starter.common.support.http.ChannelHttp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Magpie 网关的配额 / 余额读数实现。
 *
 * <p>数据源是数据面 {@code GET /v1/magpie/quotas}，其响应为
 * {@code {data:[Quota...]}}，每行对应 magpie 里的一张额度卡：</p>
 * <ul>
 *   <li>{@code kind=balance}：某个 key 的余额（{@code balance} 字符串，如 {@code "7"}）；</li>
 *   <li>{@code kind=plan}：按 key 购买的套餐；</li>
 *   <li>{@code kind=subscription}：订阅账号（Codex / Claude 等），额度按窗口给出
 *       （{@code windows:[{name,used,remaining,resetsAt,limit,amount,balance}]}）。</li>
 * </ul>
 *
 * <p>契约要求「缺省表示上游未提供，不是 0」：这里只累加能解析成数值的余额，
 * 解析不了就只放进 {@code packages} 让前端原样展示（magpie 的 {@code display}
 * 可能是 {@code "1.2k / 3k"} 这种厂商自定义文案）。整次查询失败写 {@code error}，
 * 由渠道列表继续渲染。</p>
 *
 * <p>Magpie 的签到走 {@link MagpieAccountChannelAuthManager#checkin(Map)}
 * （管理面 {@code /api/usage/*-checkin}），不是成长任务（accept/claim），
 * 故本类不实现 {@code ChannelTaskProvider}。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi(MagpieSupport.CHANNEL)
public class MagpieChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 单位：额度 / 积分。
     */
    private static final String UNIT_CREDIT = "credit";

    /**
     * 单位：货币。
     */
    private static final String UNIT_CURRENCY = "currency";

    @Override
    public String channel() {
        return MagpieSupport.CHANNEL;
    }

    @Override
    public String creditUnit() {
        return UNIT_CREDIT;
    }

    /**
     * {@inheritDoc}
     *
     * <p>永不抛异常：网关不可达、旧版本没有配额端点、响应结构变化都写 {@code error} 返回。</p>
     */
    @Override
    public Map<String, Object> credit(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(10);
        String baseUrl = MagpieSupport.baseUrl(credentials);
        String gatewayKey = MagpieSupport.gatewayKey(credentials);

        List<Map<String, Object>> quotas;
        try {
            quotas = MagpieAdminClient.quotas(baseUrl, gatewayKey);
        } catch (Exception e) {
            result.put("error", "读取 Magpie 配额失败: " + ChannelHttp.truncate(e.getMessage()));
            return result;
        }
        return snapshot(quotas);
    }

    /**
     * 把网关返回的配额卡归一化成渠道管理页的积分契约。
     *
     * <p>包内可见是为了单测：聚合逻辑（尤其「不同单位不相加」）不依赖 HTTP。</p>
     *
     * @param quotas 网关 {@code /v1/magpie/quotas} 的 {@code data} 列表
     * @return 积分快照；无可读字段时返回含 {@code error} 的 Map
     */
    static Map<String, Object> snapshot(List<Map<String, Object>> quotas) {
        Map<String, Object> result = new LinkedHashMap<>(10);
        if (quotas == null || quotas.isEmpty()) {
            result.put("error", "Magpie 未返回配额卡（确认网关已启动，且 provider 配置了余额或额度读取）");
            return result;
        }

        List<Map<String, Object>> packages = new ArrayList<>(quotas.size() * 2);
        List<String> errors = new ArrayList<>(2);
        // 不同上游的额度单位互不相通（Codex 是「请求数窗口」、Claude 是 token、
        // 充值卡是货币），把它们加成一个数会给出错误的余额。这里按单位分桶求和，
        // 顶部只报「主单位」的合计，其余单位在 packages 里各带自己的 unit / display。
        Map<String, long[]> sums = new LinkedHashMap<>(4);
        boolean anyWindowExhausted = false;
        long expiresAt = 0L;
        String plan = null;

        for (Map<String, Object> quota : quotas) {
            String provider = stringOf(quota.get("provider"));
            String name = stringOf(quota.get("name"));
            String kind = stringOf(quota.get("kind"));
            String title = displayName(provider, name);
            if (plan == null && !MagpieSupport.isBlank(stringOf(quota.get("plan")))) {
                plan = stringOf(quota.get("plan"));
            }
            String quotaError = stringOf(quota.get("error"));
            if (!MagpieSupport.isBlank(quotaError)) {
                errors.add(title + ": " + quotaError);
            }

            Long quotaBalance = parseAmount(stringOf(quota.get("balance")));
            String balanceUnit = "balance".equalsIgnoreCase(kind) ? UNIT_CURRENCY : UNIT_CREDIT;
            if (quotaBalance != null) {
                accumulate(sums, balanceUnit, quotaBalance, null);
                Map<String, Object> row = new LinkedHashMap<>(5);
                row.put("name", title);
                row.put("remain", quotaBalance);
                row.put("unit", balanceUnit);
                row.put("kind", kind);
                packages.add(row);
            }

            long quotaExpires = epochOf(quota.get("until"));
            expiresAt = earliest(expiresAt, quotaExpires);

            for (Map<String, Object> window : mapList(quota.get("windows"))) {
                if (Boolean.TRUE.equals(window.get("unlimited"))) {
                    continue;
                }
                Long windowRemain = firstAmount(window.get("balance"), window.get("remaining"));
                Long windowLimit = parseAmount(window.get("limit"));
                if (windowLimit == null) {
                    windowLimit = parseAmount(window.get("total"));
                }
                if (windowRemain == null && windowLimit != null && windowLimit > 0) {
                    Long used = parseAmount(window.get("amount"));
                    windowRemain = used == null ? null : Math.max(windowLimit - used, 0L);
                }
                String windowName = stringOf(window.get("name"));
                Map<String, Object> row = new LinkedHashMap<>(6);
                row.put("name", MagpieSupport.isBlank(windowName)
                        ? title : title + " · " + windowName);
                row.put("kind", kind);
                String unit = stringOf(window.get("unit"));
                if (MagpieSupport.isBlank(unit)) {
                    unit = UNIT_CREDIT;
                }
                row.put("unit", unit);
                if (windowRemain != null) {
                    row.put("remain", windowRemain);
                    if (windowRemain <= 0) {
                        anyWindowExhausted = true;
                    }
                }
                if (windowLimit != null && windowLimit > 0) {
                    row.put("size", windowLimit);
                }
                accumulate(sums, unit, windowRemain, windowLimit);
                String display = stringOf(window.get("display"));
                if (!MagpieSupport.isBlank(display)) {
                    row.put("display", display);
                }
                long resetsAt = epochOf(window.get("resetsAt"));
                if (resetsAt > 0) {
                    row.put("expiresAt", resetsAt);
                }
                expiresAt = earliest(expiresAt, resetsAt);
                if (windowRemain != null || windowLimit != null || !MagpieSupport.isBlank(display)) {
                    packages.add(row);
                }
            }
        }

        String headlineUnit = null;
        long remainSum = 0L;
        long totalSum = 0L;
        boolean hasRemain = false;
        boolean hasTotal = false;
        boolean anyPositive = false;
        for (Map.Entry<String, long[]> entry : sums.entrySet()) {
            long[] sum = entry.getValue();
            boolean unitRemain = sum[2] > 0;
            boolean unitTotal = sum[3] > 0;
            if (headlineUnit == null && (unitRemain || unitTotal)) {
                // 首个出现数值的单位作为顶部口径（余额卡通常排在最前）
                headlineUnit = entry.getKey();
                remainSum = sum[0];
                totalSum = sum[1];
                hasRemain = unitRemain;
                hasTotal = unitTotal;
                anyPositive = sum[0] > 0;
            }
        }

        if (!hasRemain && packages.isEmpty()) {
            result.put("error", "Magpie 配额卡里没有可读的余额字段"
                    + (errors.isEmpty() ? "" : "（" + String.join("; ", errors) + "）"));
            return result;
        }
        if (hasRemain) {
            result.put("remain", remainSum);
        }
        if (hasTotal) {
            result.put("total", totalSum);
        }
        result.put("unit", headlineUnit == null ? UNIT_CREDIT : headlineUnit);
        result.put("exceeded", hasRemain && !anyPositive && anyWindowExhausted);
        result.put("updatedAt", System.currentTimeMillis());
        if (plan != null) {
            result.put("plan", plan);
        }
        if (expiresAt > 0) {
            result.put("expiresAt", expiresAt);
        }
        if (!packages.isEmpty()) {
            result.put("packages", packages);
        }
        if (sums.size() > 1) {
            errors.add("上游额度单位不一致（" + String.join(" / ", sums.keySet())
                    + "），顶部数值仅统计 " + (headlineUnit == null ? UNIT_CREDIT : headlineUnit)
                    + "；各卡片保留自己的单位与原始 display");
        }
        if (!errors.isEmpty()) {
            result.put("warning", String.join("; ", errors));
        }
        return result;
    }

    // ── 私有辅助 ──

    /**
     * 按单位累加余额与总量。
     *
     * <p>桶结构 {@code [remain, total, remainCount, totalCount]}：
     * 计数用来区分「字段缺失」与「值为 0」，避免把未提供当成 0 汇总。</p>
     *
     * @param sums   分单位累加器
     * @param unit   单位
     * @param remain 剩余（可空）
     * @param total  总量（可空）
     */
    private static void accumulate(Map<String, long[]> sums, String unit, Long remain, Long total) {
        long[] sum = sums.computeIfAbsent(unit, key -> new long[4]);
        if (remain != null) {
            sum[0] += remain;
            sum[2] += 1;
        }
        if (total != null && total > 0) {
            sum[1] += total;
            sum[3] += 1;
        }
    }

    /**
     * 卡片标题：{@code provider} 与 {@code name} 相同则只显示一个。
     *
     * @param provider provider 段
     * @param name     卡片名
     * @return 展示名
     */
    private static String displayName(String provider, String name) {
        if (MagpieSupport.isBlank(provider)) {
            return MagpieSupport.isBlank(name) ? "Magpie 账号" : name;
        }
        if (MagpieSupport.isBlank(name) || name.equalsIgnoreCase(provider)) {
            return provider;
        }
        return provider + "/" + name;
    }

    /**
     * 取首个可解析金额。
     *
     * @param values 候选值
     * @return 金额；全不可解析返回 {@code null}
     */
    private static Long firstAmount(Object... values) {
        for (Object value : values) {
            Long amount = parseAmount(value);
            if (amount != null) {
                return amount;
            }
        }
        return null;
    }

    /**
     * 金额解析：支持数字 / 数字字符串 / 带货币符号或千分位的字符串。
     *
     * <p>不做 {@code 1.2k} 这类单位换算：magpie 的 {@code display} 才是厂商原文，
     * 换算错会把额度报告成十倍。</p>
     *
     * @param value 原始值
     * @return 金额；不可解析返回 {@code null}
     */
    private static Long parseAmount(Object value) {
        if (value instanceof Number number) {
            return Math.round(number.doubleValue());
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        String cleaned = text.trim().replace(",", "").replace("$", "")
                .replace("¥", "").replace("￥", "").replace("%", "");
        try {
            return Math.round(Double.parseDouble(cleaned));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 时间归一：数值按秒/毫秒判定，ISO 串走 {@code Instant}。
     *
     * @param value 原始值
     * @return 毫秒时间戳；不可解析返回 0
     */
    private static long epochOf(Object value) {
        if (value instanceof Number number) {
            long raw = number.longValue();
            return raw <= 0 ? 0L : (raw < 1_000_000_000_000L ? raw * 1000L : raw);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return java.time.Instant.parse(text.trim()).toEpochMilli();
            } catch (Exception ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    /**
     * 取更早的非零时刻。
     *
     * @param current 当前值
     * @param next    候选值
     * @return 更早者
     */
    private static long earliest(long current, long next) {
        if (next <= 0) {
            return current;
        }
        return current <= 0 ? next : Math.min(current, next);
    }

    /**
     * 读取字符串字段。
     *
     * @param value 原始值
     * @return 字符串；空返回 {@code null}
     */
    private static String stringOf(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return null;
    }

    /**
     * 归一化对象列表。
     *
     * @param raw 原始节点
     * @return 对象列表
     */
    private static List<Map<String, Object>> mapList(Object raw) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>(map.size());
                    map.forEach((k, v) -> {
                        if (k != null) {
                            row.put(String.valueOf(k), v);
                        }
                    });
                    rows.add(row);
                }
            }
        }
        return rows;
    }
}
