// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.credit;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.http.ChannelHttp;
import com.chua.starter.common.support.http.ChannelHttpException;
import com.chua.starter.common.support.text.json.Json;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WorkBuddy 积分查询实现。
 *
 * <p>上游两个口径（对齐 {@code workbuddy2api-panel} 的 {@code UserResource}）：</p>
 * <ul>
 *   <li>个人：{@code POST {billing}/v2/billing/meter/get-user-resource}
 *       → {@code Response.Data.Accounts[]}（周期额度优先，逐包累加）；</li>
 *   <li>企业：{@code POST {billing}/v2/billing/meter/get-enterprise-user-usage}
 *       → {@code credit}（本周期已用）+ {@code limitNum}（分配额度）。</li>
 * </ul>
 *
 * <p>读数链路自包含（与渠道鉴权管理器同一口径），不依赖其它业务模块；
 * 签到与读数是两条独立通路：本类只读，绝不触发签到动作。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Spi("workbuddy")
public class WorkBuddyCreditQueryProvider implements CreditQueryProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "workbuddy";

    /**
     * 国际版上游（billing 同域）
     */
    static final String BASE_GLOBAL = "https://www.workbuddy.ai";

    /**
     * 国内版 billing / 成长中心域
     */
    static final String BILLING_CN = "https://www.codebuddy.cn";

    /**
     * 客户端版本段（对齐官方桌面端分发包）
     */
    static final String CLIENT_VERSION = "5.5.4";

    /**
     * 国际化域名后缀（判定凭据 realm 归属）
     */
    static final String GLOBAL_DOMAIN_SUFFIX = "workbuddy.ai";

    /**
     * 凭据键：账号域
     */
    static final String HANDLE_REALM = "realm";

    @Override
    public String name() {
        return CHANNEL;
    }

    /**
     * 按渠道凭据查询余额（引擎入口）。
     *
     * @param query 请求（凭据在 {@code credentials}）
     * @return 结果；快照原样进 {@code extra}
     */
    @Override
    public CreditResult query(CreditQuery query) {
        Map<String, Object> snapshot = credit(query == null ? null : query.getCredentials());
        return CreditSnapshots.fromSnapshot(CHANNEL, snapshot);
    }

    /**
     * 查询账号积分 / 余额快照。
     *
     * <p>与签到动作分开：签到是「动作」，本方法是「读数」，
     * 渠道页要能在不签到的情况下展示余额。</p>
     *
     * @param credentials 凭据
     * @return 积分快照（{@code remain/total/unit/packages/updatedAt}）；失败带 {@code error}
     */
    public Map<String, Object> credit(Map<String, Object> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>(2);
            empty.put("error", "账号未绑定或凭据为空");
            return empty;
        }
        Map<String, Object> result = new LinkedHashMap<>(8);
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            result.put("error", "凭据缺少 access_token");
            return result;
        }
        String realm = credentialRealm(credentials);
        long[] snapshot = balanceDetailed(credentials, realm);
        if (snapshot == null) {
            result.put("error", "上游未返回可用余额（个人资源包与企业配额均未命中）");
            return result;
        }
        result.put("remain", snapshot[0]);
        result.put("total", snapshot[1]);
        result.put("unit", "credit");
        result.put("realm", realm);
        result.put("plan", "global".equals(realm) ? "WorkBuddy AI" : "WorkBuddy");
        result.put("exceeded", snapshot[1] >= 0 && snapshot[0] <= 0);
        result.put("updatedAt", System.currentTimeMillis());
        List<Map<String, Object>> packages = balancePackages(credentials, realm);
        if (!packages.isEmpty()) {
            result.put("packages", packages);
        }
        return result;
    }

    /**
     * 余额明细查询（带原始资源包列表，供积分明细展示）。
     *
     * @param credentials 凭据
     * @param realm       账号域
     * @return {@code [剩余, 总额]}；失败返回 {@code null}
     */
    private static long[] balanceDetailed(Map<String, Object> credentials, String realm) {
        try {
            Map<String, String> headers = billingHeaders(credentials, realm);
            if (!isBlank(readString(credentials, "enterprise_id"))) {
                String body = ChannelHttp.postJson(
                        billingOrigin(realm) + "/v2/billing/meter/get-enterprise-user-usage",
                        "{}", headers);
                return enterpriseBalance(parseJson(body));
            }
            String body = ChannelHttp.postJson(
                    billingOrigin(realm) + "/v2/billing/meter/get-user-resource",
                    Json.toJson(userResourceRequest()), headers);
            return personalBalance(parseJson(body));
        } catch (ChannelHttpException e) {
            log.debug("[workbuddy] 余额查询失败: {}", ChannelHttp.truncate(e.getMessage()));
            return null;
        }
    }

    /**
     * 个人资源包明细（{@code Response.Data.Accounts[]} 逐条归一化）。
     *
     * @param credentials 凭据
     * @param realm       账号域
     * @return 明细列表；查询失败返回空
     */
    private static List<Map<String, Object>> balancePackages(Map<String, Object> credentials, String realm) {
        if (!isBlank(readString(credentials, "enterprise_id"))) {
            return List.of();
        }
        try {
            String body = ChannelHttp.postJson(
                    billingOrigin(realm) + "/v2/billing/meter/get-user-resource",
                    Json.toJson(userResourceRequest()), billingHeaders(credentials, realm));
            Map<String, Object> envelope = parseJson(body);
            if (!(envelope.get("Response") instanceof Map<?, ?> response)
                    || !(response.get("Data") instanceof Map<?, ?> data)
                    || !(data.get("Accounts") instanceof List<?> accounts)) {
                return List.of();
            }
            List<Map<String, Object>> packages = new ArrayList<>(accounts.size());
            for (Object item : accounts) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> account = asMap(raw);
                long[] remainUsed = packageRemainUsed(account);
                Map<String, Object> row = new LinkedHashMap<>(4);
                String name = readString(account, "PackageName");
                row.put("name", isBlank(name) ? "资源包" : name);
                row.put("remain", remainUsed[0]);
                row.put("size", remainUsed[1]);
                String cycleEnd = readString(account, "CycleEndTime");
                if (!isBlank(cycleEnd)) {
                    row.put("expiresAt", cycleEnd);
                }
                packages.add(row);
            }
            return packages;
        } catch (ChannelHttpException e) {
            log.debug("[workbuddy] 资源包明细查询失败: {}", ChannelHttp.truncate(e.getMessage()));
            return List.of();
        }
    }

    /**
     * 个人资源包查询请求体（与 panel 的 {@code UserResource} 同口径）。
     *
     * @return 请求体
     */
    private static Map<String, Object> userResourceRequest() {
        Map<String, Object> request = new LinkedHashMap<>(6);
        request.put("PageNumber", 1);
        request.put("PageSize", 100);
        request.put("ProductCode", "p_tcaca");
        request.put("Status", List.of(0, 3));
        return request;
    }

    /**
     * 单资源包的 {@code [剩余, 总量]}（周期额度优先，缺省退总量）。
     *
     * @param account 资源包节点
     * @return {@code [剩余, 总量]}
     */
    private static long[] packageRemainUsed(Map<String, Object> account) {
        Long cycleRemain = toLong(account.get("CycleCapacityRemain"));
        Long capacityRemain = toLong(account.get("CapacityRemain"));
        Long cycleSize = toLong(account.get("CycleCapacitySize"));
        Long capacitySize = toLong(account.get("CapacitySize"));
        long remain = firstNonNull(cycleRemain, capacityRemain, 0L);
        long size = firstNonNull(cycleSize, capacitySize, remain);
        if (remain < 0) {
            remain = 0L;
        }
        if (size < remain) {
            size = remain;
        }
        return new long[]{remain, size};
    }

    /**
     * 解析个人资源包余额（{@code Response.Data.Accounts[]} 聚合）。
     *
     * @param envelope 响应信封
     * @return {@code [剩余, 总额]}；结构异常返回 {@code null}
     */
    private static long[] personalBalance(Map<String, Object> envelope) {
        if (!(envelope.get("Response") instanceof Map<?, ?> response)
                || !(response.get("Data") instanceof Map<?, ?> data)
                || !(data.get("Accounts") instanceof List<?> accounts)) {
            return null;
        }
        long remain = 0L;
        long total = 0L;
        for (Object item : accounts) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            long[] pair = packageRemainUsed(asMap(raw));
            remain += pair[0];
            total += pair[1];
        }
        return new long[]{remain, total};
    }

    /**
     * 解析企业配额（{@code credit} 是本周期已用，{@code limitNum} 是分配额度）。
     *
     * @param envelope 响应信封
     * @return {@code [剩余, 总额]}；结构异常返回 {@code null}
     */
    private static long[] enterpriseBalance(Map<String, Object> envelope) {
        Object data = envelope.get("data");
        Map<String, Object> node = data instanceof Map<?, ?> map ? asMap(map) : envelope;
        Long credit = toLong(node.get("credit"));
        Long limit = firstNonNullLong(toLong(node.get("limitNum")), toLong(node.get("limit_num")));
        if (limit == null) {
            return null;
        }
        if (limit < 0) {
            // 不限量：与 panel 口径一致，剩余给足够大的值，总额记 -1 表示「不限」
            return new long[]{1L << 40, -1L};
        }
        long used = credit == null ? 0L : Math.round(credit);
        long remain = limit - used;
        return new long[]{Math.max(remain, 0L), limit};
    }

    /**
     * 归一化账号域：仅 {@code global} 保留，其余视为 {@code cn}。
     *
     * @param realm 原始值
     * @return 归一化结果
     */
    private static String resolveRealm(String realm) {
        return "global".equalsIgnoreCase(realm == null ? "" : realm.trim()) ? "global" : "cn";
    }

    /**
     * 从凭据推断账号域：显式 {@code realm} → {@code domain}/{@code base_url} 后缀 → cn。
     *
     * @param credentials 凭据
     * @return 账号域
     */
    private static String credentialRealm(Map<String, Object> credentials) {
        String realm = readString(credentials, HANDLE_REALM);
        if (!isBlank(realm)) {
            return resolveRealm(realm);
        }
        String domain = readString(credentials, "domain");
        if (!isBlank(domain) && domain.contains(GLOBAL_DOMAIN_SUFFIX)) {
            return "global";
        }
        String baseUrl = readString(credentials, "base_url");
        if (!isBlank(baseUrl) && baseUrl.contains(GLOBAL_DOMAIN_SUFFIX)) {
            return "global";
        }
        return "cn";
    }

    /**
     * billing / 成长中心根地址（CN 与 chat 不同域）。
     *
     * @param realm 账号域
     * @return 根地址
     */
    private static String billingOrigin(String realm) {
        return "global".equals(realm) ? BASE_GLOBAL : BILLING_CN;
    }

    /**
     * billing 域请求头（对齐 {@code BillingHeaders}：单段 UA + 账号域头）。
     *
     * @param credentials 凭据
     * @param realm       账号域
     * @return 请求头
     */
    private static Map<String, String> billingHeaders(Map<String, Object> credentials, String realm) {
        Map<String, String> headers = new LinkedHashMap<>(8);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("X-CodeBuddy-Request", "1");
        headers.put("Accept-Language", "global".equals(realm) ? "en-US" : "zh-CN");
        headers.put("User-Agent", "WorkBuddy/" + CLIENT_VERSION);
        headers.put("Authorization", "Bearer " + readString(credentials, "access_token"));
        String uid = readString(credentials, "uid");
        if (!isBlank(uid)) {
            headers.put("X-User-Id", uid);
        }
        String enterpriseId = readString(credentials, "enterprise_id");
        if (!isBlank(enterpriseId)) {
            headers.put("X-Enterprise-Id", enterpriseId);
            headers.put("X-Tenant-Id", enterpriseId);
        }
        String domain = readString(credentials, "domain");
        if (!isBlank(domain)) {
            headers.put("X-Domain", domain);
        }
        return headers;
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
     * Map 键归一化为字符串键。
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
     * 读取非空字符串。
     *
     * @param source 源 Map
     * @param key    键
     * @return 值；不存在或空串返回 {@code null}
     */
    private static String readString(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return null;
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

    /**
     * 转为 Long。
     *
     * @param value 原始值
     * @return 长整数；无法转换返回 {@code null}
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
     * 取首个非空 Long。
     *
     * @param values 候选值
     * @return 首个非空值；全空返回 {@code null}
     */
    private static Long firstNonNullLong(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 取首个非空 Long（带默认值）。
     *
     * @param first      首选
     * @param second     次选
     * @param defaultVal 默认值
     * @return 生效值
     */
    private static long firstNonNull(Long first, Long second, long defaultVal) {
        if (first != null) {
            return first;
        }
        return second == null ? defaultVal : second;
    }
}
