// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelCreditProvider;
import com.chua.starter.common.support.channel.MagpieSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    /**
     * 单位：Token。
     */
    private static final String UNIT_TOKEN = "token";

    /**
     * magpie 用 ISO 币种代码表示货币窗口（{@code usd} / {@code cny}）。
     *
     * <p>这些必须归到 {@link #UNIT_CURRENCY} 统一处理，否则「美元余额」会被当成
     * 一个独立单位，顶部既不能和别的货币相加，也不能标成货币。</p>
     */
    private static final Set<String> CURRENCY_UNITS = Set.of(
            "usd", "eur", "cny", "rmb", "jpy", "gbp", "hkd", "sgd");

    /**
     * 分桶键分隔符：{@code unit|currency}，没有币种时只有 {@code unit}。
     */
    private static final String BUCKET_SEPARATOR = "|";

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
        return creditFor(vendor, null, null, credentials);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code account} 非空时只保留该账号的额度卡（网关上同一家上游可能登了多个
     * 账号，账号卡各自显示自己的余额）；为空时是这一家上游的合计。</p>
     */
    @Override
    public Map<String, Object> creditFor(String vendor, String account, Map<String, Object> credentials) {
        return creditFor(vendor, null, account, credentials);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code provider} 非空时再按上游实例收窄：一个业务渠道在网关里可能挂了多个
     * 上游（Trae 国内 / 国际），账号行是按上游展开的，不收窄会让两行都显示整家合计。</p>
     */
    @Override
    public Map<String, Object> creditFor(String vendor, String provider, String account,
                                         Map<String, Object> credentials) {
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
                    return snapshotOf(scoped(adminQuotas, vendor, provider, account), vendor, account);
                }
            } catch (Exception ignored) {
                // 页面 Key 失效 / 管理面没开：继续走数据面
            }
        }

        List<Map<String, Object>> quotas;
        try {
            quotas = MagpieAdminClient.quotas(baseUrl, gatewayKey);
        } catch (Exception e) {
            result.put("error", "余额查询失败，请稍后重试");
            return result;
        }
        return snapshotOf(scoped(quotas, vendor, provider, account), vendor, account);
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
    private static List<Map<String, Object>> scoped(List<Map<String, Object>> quotas, String vendor,
                                                   String provider, String account) {
        if (MagpieSupport.isBlank(vendor) || quotas == null || quotas.isEmpty()) {
            return quotas;
        }
        List<String> wanted = MagpieSupport.providerIdsOf(vendor);
        List<Map<String, Object>> hit = new ArrayList<>(quotas.size());
        for (Map<String, Object> quota : quotas) {
            String quotaProvider = stringOf(quota.get("provider"));
            if (quotaProvider == null) {
                continue;
            }
            if (!MagpieSupport.isBlank(provider)) {
                // 账号行按上游展开：只留这一个上游实例的卡，否则两行都显示整家合计。
                // provider 必须是本渠道的候选之一，避免拿别的渠道 id 越界读数。
                if (!provider.equalsIgnoreCase(quotaProvider) || !containsIgnoreCase(wanted, provider)) {
                    continue;
                }
            } else {
                boolean providerHit = false;
                for (String id : wanted) {
                    if (id.equalsIgnoreCase(quotaProvider)) {
                        providerHit = true;
                        break;
                    }
                }
                if (!providerHit) {
                    continue;
                }
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
            result.put("error", "「" + scope + "」暂时读不到余额，请稍后重试");
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
            result.put("error", "暂时读不到余额，请稍后重试");
            return result;
        }

        List<Map<String, Object>> packages = new ArrayList<>(quotas.size() * 2);
        List<String> errors = new ArrayList<>(2);
        // 上游只给文案（"0 credits left" / "$0.0064" / "92 魔粒"）而没有数值时，
        // 用这句原文当卡片主读数，比「额度未知」有用得多。
        String headlineText = null;
        // 不同上游的额度单位互不相通（Codex 是「请求数窗口」、Claude 是 token、
        // 充值卡是货币），把它们加成一个数会给出错误的余额。这里按「单位 + 币种」
        // 分桶求和，每个桶都通过 {@code units} 交给前端分别展示；缺单位的窗口只
        // 进明细，不进任何桶。
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
                // 上游给的是插件 / 进程级原文（退出码、URL、HTML 片段），不往界面透；
                // 卡片上只说「这一家读不到」，细节由上游日志承担。
                errors.add(title);
            }

            String rawBalance = stringOf(quota.get("balance"));
            Long quotaBalance = isPlainAmount(rawBalance) ? parseAmount(rawBalance) : null;
            if (quotaBalance != null) {
                String balanceUnit = "balance".equalsIgnoreCase(kind) ? UNIT_CURRENCY : UNIT_CREDIT;
                accumulate(sums, balanceUnit, null, quotaBalance, null);
                Map<String, Object> row = new LinkedHashMap<>(5);
                row.put("name", title);
                row.put("remain", quotaBalance);
                row.put("unit", balanceUnit);
                row.put("kind", kind);
                packages.add(row);
            } else if (!MagpieSupport.isBlank(rawBalance)) {
                // 余额是厂商文案（带币种符号 / 单位词 / 小数），保留原文展示；
                // 早先按整数解析会把 $0.0064 抹成 0，余额直接变「用光」。
                Map<String, Object> row = new LinkedHashMap<>(5);
                row.put("name", title);
                row.put("display", rawBalance);
                String balanceCurrency = currencyOfSymbol(rawBalance);
                if (balanceCurrency != null) {
                    row.put("unit", UNIT_CURRENCY);
                    row.put("currency", balanceCurrency);
                } else if ("balance".equalsIgnoreCase(kind)) {
                    row.put("unit", UNIT_CURRENCY);
                }
                row.put("kind", kind);
                packages.add(row);
                if (headlineText == null) {
                    headlineText = rawBalance;
                }
            }

            long quotaExpires = epochOf(quota.get("until"));
            expiresAt = earliest(expiresAt, quotaExpires);

            // 先识别「汇总行」：magpie 的 workbuddy 卡同时给出
            // 「Credits 500 / Extra credits 3936 / Total credits 4436」，把三行都累加
            // 会把可用额度报成两倍（实际 4436）。判定见 aggregateWindowIndexes。
            List<Map<String, Object>> windows = mapList(quota.get("windows"));
            Set<Integer> aggregateIndexes = aggregateWindowIndexes(windows);
            for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
                Map<String, Object> window = windows.get(windowIndex);
                if (Boolean.TRUE.equals(window.get("unlimited"))) {
                    continue;
                }
                Long windowLimit = firstAmount(window.get("limit"), window.get("total"));
                // magpie 的窗口字段：amount 是「剩余量」，used 是「剩余百分比」
                // （实测 48.333333 对应 145/300，100 对应 500/500）。早期把 used 当成
                // 已用量去减总量，结果 Qoder 报 252/300、WorkBuddy 报 8604/8872。
                Long windowRemain = firstAmount(
                        window.get("balance"), window.get("remaining"), window.get("amount"));
                if (windowRemain == null) {
                    windowRemain = percentRemain(windowLimit, window.get("used"));
                }
                String windowName = stringOf(window.get("name"));
                Map<String, Object> row = new LinkedHashMap<>(7);
                row.put("name", MagpieSupport.isBlank(windowName)
                        ? title : title + " · " + windowName);
                row.put("kind", kind);
                String rawUnit = stringOf(window.get("unit"));
                String windowUnit = canonicalUnit(rawUnit);
                String windowCurrency = currencyOfUnit(rawUnit);
                // 没给单位的窗口（magpie 的 token 估算窗）只展示明细，不进合计：
                // 猜成 credit 会把 200M token 加到积分上，顶部数字直接失真。
                if (windowUnit != null) {
                    row.put("unit", windowUnit);
                }
                if (windowCurrency != null) {
                    row.put("currency", windowCurrency);
                }
                if (windowRemain != null) {
                    row.put("remain", windowRemain);
                    if (windowRemain <= 0) {
                        anyWindowExhausted = true;
                    }
                }
                if (windowLimit != null && windowLimit > 0) {
                    row.put("size", windowLimit);
                }
                if (windowUnit != null && !aggregateIndexes.contains(windowIndex)) {
                    accumulate(sums, windowUnit, windowCurrency, windowRemain, windowLimit);
                }
                String display = stringOf(window.get("display"));
                if (!MagpieSupport.isBlank(display)) {
                    row.put("display", display);
                    if (windowRemain == null && headlineText == null) {
                        // 只有文案没有数值（"0 credits left" 等）：拿它当主读数
                        headlineText = display;
                    }
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

        List<Map<String, Object>> units = new ArrayList<>(sums.size());
        String headlineUnit = null;
        String headlineCurrency = null;
        long remainSum = 0L;
        long totalSum = 0L;
        boolean hasRemain = false;
        boolean hasTotal = false;
        boolean anyPositive = false;
        for (Map.Entry<String, long[]> entry : sums.entrySet()) {
            long[] sum = entry.getValue();
            boolean unitRemain = sum[2] > 0;
            boolean unitTotal = sum[3] > 0;
            if (!unitRemain && !unitTotal) {
                continue;
            }
            String[] unitAndCurrency = splitBucketKey(entry.getKey());
            Map<String, Object> row = new LinkedHashMap<>(4);
            row.put("unit", unitAndCurrency[0]);
            if (unitAndCurrency[1] != null) {
                row.put("currency", unitAndCurrency[1]);
            }
            if (unitRemain) {
                row.put("remain", sum[0]);
            }
            if (unitTotal) {
                row.put("total", sum[1]);
            }
            units.add(row);
            if (headlineUnit == null) {
                // 首个出现数值的单位作为卡片口径（余额卡通常排在最前）；
                // 顶部条会按 {@code units} 把每种单位各显示一条。
                headlineUnit = unitAndCurrency[0];
                headlineCurrency = unitAndCurrency[1];
                remainSum = sum[0];
                totalSum = sum[1];
                hasRemain = unitRemain;
                hasTotal = unitTotal;
                anyPositive = sum[0] > 0;
            }
        }

        if (!hasRemain && packages.isEmpty()) {
            result.put("error", "暂时读不到余额，请稍后重试");
            return result;
        }
        if (!hasRemain && headlineText != null) {
            // 数值读不出来但有原文：主读数直接显示上游文案（"0 credits left"）
            result.put("text", headlineText);
        }
        if (hasRemain) {
            result.put("remain", remainSum);
        }
        if (hasTotal) {
            result.put("total", totalSum);
        }
        result.put("unit", headlineUnit == null ? UNIT_CREDIT : headlineUnit);
        if (headlineCurrency != null) {
            result.put("currency", headlineCurrency);
        }
        if (!units.isEmpty()) {
            result.put("units", units);
        }
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
        if (!errors.isEmpty()) {
            result.put("warning", "「" + String.join("」「", errors) + "」余额读取失败");
        }
        return result;
    }

    // ── 私有辅助 ──

    /**
     * 找出「汇总行」：名字表示合计、且总量正好等于<b>同一单位</b>下其余窗口之和的那一行。
     *
     * <p>magpie 会把合计再列一遍（{@code Credits 500 + Extra credits 3936 + Total credits 4436}），
     * 全部相加会把可用额度报成两倍。只在数值上严格成立时才归并，且必须满足：</p>
     * <ul>
     *   <li>行名是「合计」语义（{@code Total credits} / 总计 / 汇总 …）——只看数值的话，
     *       「10 + 10 + 20」这种合法分桶会被误判成汇总；</li>
     *   <li>同一单位下至少三行有正的总量（两行时 1+1=2 也可能成立，容易误判）；</li>
     *   <li>按单位分别判定：同一张卡里的 token 估算窗不会破坏 credits 的合计；</li>
     *   <li>同一单位里满足条件的行<b>只有一行</b>，两行都满足视为有歧义，不归并。</li>
     * </ul>
     *
     * <p>被判定为汇总的行仍会出现在 {@code packages} 里（保留上游 display），只是不计入合计。</p>
     *
     * @param windows 同一张配额卡的窗口列表
     * @return 汇总行的下标集合；没有返回空集合
     */
    private static Set<Integer> aggregateWindowIndexes(List<Map<String, Object>> windows) {
        Map<String, Long> unitSums = new LinkedHashMap<>(4);
        Map<String, Integer> unitCounts = new LinkedHashMap<>(4);
        List<String> units = new ArrayList<>(windows.size());
        for (Map<String, Object> window : windows) {
            String unit = canonicalUnit(stringOf(window.get("unit")));
            units.add(unit);
            if (unit == null || Boolean.TRUE.equals(window.get("unlimited"))) {
                continue;
            }
            Long limit = firstAmount(window.get("limit"), window.get("total"));
            if (limit == null || limit <= 0) {
                continue;
            }
            unitSums.merge(unit, limit, Long::sum);
            unitCounts.merge(unit, 1, Integer::sum);
        }
        Map<String, Integer> foundAt = new LinkedHashMap<>(4);
        Set<String> ambiguous = new HashSet<>(2);
        for (int index = 0; index < windows.size(); index++) {
            String unit = units.get(index);
            if (unit == null || ambiguous.contains(unit)) {
                continue;
            }
            Integer count = unitCounts.get(unit);
            Long unitSum = unitSums.get(unit);
            Long limit = firstAmount(windows.get(index).get("limit"), windows.get(index).get("total"));
            if (count == null || count < 3 || unitSum == null || limit == null) {
                continue;
            }
            if (limit * 2 != unitSum || !isTotalLabel(stringOf(windows.get(index).get("name")))) {
                continue;
            }
            if (foundAt.containsKey(unit)) {
                foundAt.remove(unit);
                ambiguous.add(unit);
            } else {
                foundAt.put(unit, index);
            }
        }
        return new HashSet<>(foundAt.values());
    }

    /**
     * 列表里是否存在该值（忽略大小写）。
     *
     * @param values 候选值
     * @param target 目标值
     * @return 命中返回 true
     */
    private static boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 单位归一：magpie 同一量纲会写成 {@code credit} / {@code credits}，
     * 货币会写成 {@code usd} / {@code cny}，必须各自并成一个桶，
     * 否则顶部会同时出现「积分」和「credits」两条。
     *
     * @param unit 上游单位
     * @return 归一后的单位；没有单位返回 {@code null}
     */
    private static String canonicalUnit(String unit) {
        if (MagpieSupport.isBlank(unit)) {
            return null;
        }
        String text = unit.trim().toLowerCase(Locale.ROOT);
        if ("credit".equals(text) || "credits".equals(text) || "积分".equals(text)) {
            return UNIT_CREDIT;
        }
        if ("token".equals(text) || "tokens".equals(text)) {
            return UNIT_TOKEN;
        }
        if (CURRENCY_UNITS.contains(text)) {
            return UNIT_CURRENCY;
        }
        return text;
    }

    /**
     * 货币单位对应的币种代码（{@code USD} / {@code CNY} …）。
     *
     * @param unit 上游单位
     * @return 币种；非货币单位返回 {@code null}
     */
    private static String currencyOfUnit(String unit) {
        if (MagpieSupport.isBlank(unit)) {
            return null;
        }
        String text = unit.trim().toLowerCase(Locale.ROOT);
        return CURRENCY_UNITS.contains(text) ? text.toUpperCase(Locale.ROOT) : null;
    }

    /**
     * 用「剩余百分比」兜底算剩余量。
     *
     * <p>magpie 窗口的 {@code used} 是剩余百分比（实测 {@code 48.333333} 表示
     * 145/300，{@code 100} 表示 500/500），只在没有 {@code amount} 时使用；
     * 取值不在 0~100 说明不是百分比，宁可报「未知」也不猜。</p>
     *
     * @param limit 窗口总量
     * @param used  上游的剩余百分比
     * @return 剩余量；无法判断返回 {@code null}
     */
    private static Long percentRemain(Long limit, Object used) {
        if (limit == null || limit <= 0) {
            return null;
        }
        Double percent = asDecimal(used);
        if (percent == null || percent < 0 || percent > 100) {
            return null;
        }
        return Math.round(limit * percent / 100D);
    }

    /**
     * 转成小数。
     *
     * @param value 原始值（数字 / 数字字符串，可带 %）
     * @return 小数；不可解析返回 {@code null}
     */
    private static Double asDecimal(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim().replace("%", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 分桶键：{@code unit} 或 {@code unit|currency}。
     *
     * <p>货币必须按币种分桶，USD 与 CNY 是不同的量，不能相加。</p>
     *
     * @param unit     归一后的单位
     * @param currency 币种（可空）
     * @return 分桶键
     */
    private static String bucketKey(String unit, String currency) {
        return currency == null ? unit : unit + BUCKET_SEPARATOR + currency;
    }

    /**
     * 拆分分桶键。
     *
     * @param key 分桶键
     * @return {@code [unit, currency]}，没有币种时第二项为 {@code null}
     */
    private static String[] splitBucketKey(String key) {
        int at = key.indexOf(BUCKET_SEPARATOR);
        if (at < 0) {
            return new String[]{key, null};
        }
        return new String[]{key.substring(0, at), key.substring(at + 1)};
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
     * 按「单位 + 币种」累加余额与总量。
     *
     * <p>桶结构 {@code [remain, total, remainCount, totalCount]}：
     * 计数用来区分「字段缺失」与「值为 0」，避免把未提供当成 0 汇总。</p>
     *
     * @param sums     分桶累加器
     * @param unit     归一后的单位
     * @param currency 币种（非货币单位可空）
     * @param remain   剩余（可空）
     * @param total    总量（可空）
     */
    private static void accumulate(Map<String, long[]> sums, String unit, String currency,
                                   Long remain, Long total) {
        if (remain == null && (total == null || total <= 0)) {
            // 什么都没有的窗口不建桶：否则会凭空多出一个「credit」单位
            return;
        }
        long[] sum = sums.computeIfAbsent(bucketKey(unit, currency), key -> new long[4]);
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
            return MagpieSupport.isBlank(name) ? "账号" : name;
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
     * 是否「纯整数金额」。
     *
     * <p>卡片级 {@code balance} 常是厂商文案（{@code "$0.0064"}、{@code "92 魔粒"}、
     * {@code "4.77 credits (long-term 4.77)"}）。这些按整数解析会把余额抹成 0，
     * 让人以为额度用光；只有纯整数才走数值通道，其余保留原文展示。</p>
     *
     * @param value 原始值
     * @return 纯整数 / 数字类型返回 true
     */
    private static boolean isPlainAmount(Object value) {
        if (value instanceof Number) {
            return true;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return false;
        }
        return text.trim().replace(",", "").matches("[-+]?\\d+");
    }

    /**
     * 从文案里认币种符号（{@code $} / {@code ¥} / {@code €} / {@code £}）。
     *
     * @param text 余额原文
     * @return ISO 币种；不是货币文案返回 {@code null}
     */
    private static String currencyOfSymbol(String text) {
        if (MagpieSupport.isBlank(text)) {
            return null;
        }
        if (text.indexOf('$') >= 0) {
            return "USD";
        }
        if (text.indexOf('¥') >= 0 || text.indexOf('￥') >= 0) {
            return "CNY";
        }
        if (text.indexOf('€') >= 0) {
            return "EUR";
        }
        return text.indexOf('£') >= 0 ? "GBP" : null;
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
