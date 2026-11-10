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
 * <p>数据源优先是管理面 {@code GET /api/usage/quotas}（含签到状态与插件账号），
 * 未配页面 Key 或读到空时回落到数据面 {@code GET /v1/magpie/quotas}，其响应为
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
        return creditFor(null, credentials);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code vendor} 非空时只保留该业务渠道自己的上游额度卡：网关托管渠道的页面在
     * 「Qoder」那一行只能显示 Qoder 的额度，把网关里所有上游加起来会把数字报大，
     * 也会让用户以为一个账号有这么多额度。</p>
     */
    @Override
    public Map<String, Object> creditFor(String vendor, Map<String, Object> credentials) {
        return creditFor(vendor, null, credentials);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code account} 非空时只保留该账号的额度卡（网关上同一家上游可能登了多个
     * 账号，账号卡各自显示自己的余额）；为空时是这一家上游的合计。</p>
     */
    @Override
    public Map<String, Object> creditFor(String vendor, String account, Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(10);
        String baseUrl = MagpieSupport.baseUrl(credentials);
        String gatewayKey = MagpieSupport.gatewayKey(credentials);
        String pageKey = MagpieSupport.pageKey(credentials);

        // 管理面的 /api/usage/quotas 比数据面多出签到状态与插件账号，
        // 配了页面 Key 就优先用它；读不到或为空时回落到数据面，保持旧行为。
        if (!MagpieSupport.isBlank(pageKey)) {
            try {
                List<Map<String, Object>> adminQuotas = MagpieAdminClient.adminQuotas(credentials, null, null);
                if (!adminQuotas.isEmpty()) {
                    return snapshotOf(scoped(adminQuotas, vendor, account), vendor, account);
                }
            } catch (Exception ignored) {
                // 页面 Key 失效 / 管理面没开：继续走数据面
            }
        }

        List<Map<String, Object>> quotas;
        try {
            quotas = MagpieAdminClient.quotas(baseUrl, gatewayKey);
        } catch (Exception e) {
            result.put("error", "读取 Magpie 配额失败: " + ChannelHttp.truncate(e.getMessage()));
            return result;
        }
        return snapshotOf(scoped(quotas, vendor, account), vendor, account);
    }

    /**
     * 按业务渠道 + 账号过滤额度卡：只留 {@code provider} 命中该渠道候选 id 的行，
     * 指定账号时再按 {@code user} 收窄。
     *
     * <p>候选 id 来自 {@link MagpieSupport#providerIdsOf(String)}，与前端左栏同一份对照表；
     * {@code vendor} 为空时原样返回（网关渠道自己的读数要的是全量）。</p>
     *
     * @param quotas 网关返回的额度卡
     * @param vendor 业务渠道标识
     * @param account 上游账号（可空）
     * @return 过滤后的额度卡
     */
    private static List<Map<String, Object>> scoped(List<Map<String, Object>> quotas, String vendor, String account) {
        if (MagpieSupport.isBlank(vendor) || quotas == null || quotas.isEmpty()) {
            return quotas;
        }
        List<String> wanted = MagpieSupport.providerIdsOf(vendor);
        List<Map<String, Object>> hit = new ArrayList<>(quotas.size());
        for (Map<String, Object> quota : quotas) {
            String provider = stringOf(quota.get("provider"));
            if (provider == null) {
                continue;
            }
            boolean providerHit = false;
            for (String id : wanted) {
                if (id.equalsIgnoreCase(provider)) {
                    providerHit = true;
                    break;
                }
            }
            if (!providerHit) {
                continue;
            }
            if (!MagpieSupport.isBlank(account)) {
                String user = stringOf(quota.get("user"));
                if (user == null || !account.equalsIgnoreCase(user)) {
                    continue;
                }
            }
            hit.add(quota);
        }
        return hit;
    }

    /**
     * 过滤后的快照：命中为空时给出「该上游没有额度卡」的明确原因。
     *
     * <p>直接复用 {@link #snapshot(List)} 会报「Magpie 未返回配额卡」，读起来像网关整体故障；
     * 但这里只是这一家上游没配余额读取。</p>
     *
     * @param quotas 过滤后的额度卡
     * @param vendor 业务渠道标识（空表示全量）
     * @param account 上游账号（空表示不按账号收窄）
     * @return 积分快照
     */
    private static Map<String, Object> snapshotOf(List<Map<String, Object>> quotas, String vendor, String account) {
        if (!MagpieSupport.isBlank(vendor) && (quotas == null || quotas.isEmpty())) {
            Map<String, Object> result = new LinkedHashMap<>(4);
            String scope = MagpieSupport.isBlank(account) ? vendor : vendor + " / " + account;
            result.put("error", "网关里没有「" + scope + "」的额度卡（确认该上游已登录，或到网关里补上余额读取）");
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

            // 先量一遍各窗口的总量，识别「汇总行」：magpie 的 workbuddy 卡同时给出
            // 「Credits 100 / Extra credits 90 / Total credits 190」，把三行都累加会把
            // 可用额度报成 380（实际 190）。判定见 aggregateWindowIndex。
            List<Map<String, Object>> windows = mapList(quota.get("windows"));
            int aggregateIndex = aggregateWindowIndex(windows);
            for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
                Map<String, Object> window = windows.get(windowIndex);
                if (Boolean.TRUE.equals(window.get("unlimited"))) {
                    continue;
                }
                Long windowRemain = firstAmount(window.get("balance"), window.get("remaining"));
                Long windowLimit = parseAmount(window.get("limit"));
                if (windowLimit == null) {
                    windowLimit = parseAmount(window.get("total"));
                }
                if (windowRemain == null && windowLimit != null && windowLimit > 0) {
                    // 上游字段叫 used（见 magpie /api/usage/quotas 的 windows[]），
                    // 早期版本还发过 amount，两个都认，否则剩余永远是「未知」。
                    Long used = firstAmount(window.get("used"), window.get("amount"));
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
                if (windowIndex != aggregateIndex) {
                    accumulate(sums, unit, windowRemain, windowLimit);
                }
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
     * 找出「汇总行」：名字表示合计、且总量正好等于同一张卡里其余窗口之和的那一行。
     *
     * <p>magpie 会把合计再列一遍（{@code Credits 100 + Extra credits 90 + Total credits 190}），
     * 全部相加会把可用额度报成两倍。只在<b>数值上严格成立</b>时才归并，且必须满足：</p>
     * <ul>
     *   <li>行名是「合计」语义（{@code Total credits} / 总计 / 汇总 …）——只看数值的话，
     *       「10 + 10 + 20」这种合法分桶会被误判成汇总；</li>
     *   <li>至少三行有正的总量（两行时 1+1=2 也可能成立，容易误判）；</li>
     *   <li>所有有量的行单位一致（不同单位相加本来就无意义）；</li>
     *   <li>满足条件的行<b>只有一行</b>，两行都满足视为有歧义，不归并。</li>
     * </ul>
     *
     * <p>被判定为汇总的行仍会出现在 {@code packages} 里（保留上游 display），只是不计入顶部合计。</p>
     *
     * @param windows 同一张配额卡的窗口列表
     * @return 汇总行的下标；没有或不唯一返回 {@code -1}
     */
    private static int aggregateWindowIndex(List<Map<String, Object>> windows) {
        if (windows.size() < 3) {
            return -1;
        }
        List<Long> limits = new ArrayList<>(windows.size());
        String limitUnit = null;
        boolean unitConflict = false;
        long sum = 0L;
        int positive = 0;
        for (Map<String, Object> window : windows) {
            if (Boolean.TRUE.equals(window.get("unlimited"))) {
                limits.add(null);
                continue;
            }
            Long limit = parseAmount(window.get("limit"));
            if (limit == null) {
                limit = parseAmount(window.get("total"));
            }
            if (limit == null || limit <= 0) {
                limits.add(null);
                continue;
            }
            limits.add(limit);
            positive++;
            sum += limit;
            String unit = stringOf(window.get("unit"));
            if (!MagpieSupport.isBlank(unit)) {
                if (limitUnit == null) {
                    limitUnit = unit;
                } else if (!limitUnit.equalsIgnoreCase(unit)) {
                    unitConflict = true;
                }
            }
        }
        if (positive < 3 || unitConflict) {
            return -1;
        }
        int found = -1;
        for (int index = 0; index < limits.size(); index++) {
            Long limit = limits.get(index);
            if (limit == null || limit * 2 != sum || !isTotalLabel(stringOf(windows.get(index).get("name")))) {
                continue;
            }
            if (found >= 0) {
                return -1;
            }
            found = index;
        }
        return found;
    }

    /**
     * 行名是否为「合计」语义。
     *
     * @param name 窗口名
     * @return 表示合计返回 true
     */
    private static boolean isTotalLabel(String name) {
        if (MagpieSupport.isBlank(name)) {
            return false;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("total") || name.contains("合计")
                || name.contains("总计") || name.contains("汇总");
    }

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
