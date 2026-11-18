// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.credit;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.http.ChannelHttp;
import com.chua.starter.common.support.text.json.Json;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TRAE 积分 / 权益查询实现。
 *
 * <p>口径对齐 {@code trae2api-hub} 的 {@code trae_client.fetch_entitlement} +
 * {@code trae_tasks._digest_entitlement}：</p>
 * <ul>
 *   <li>国内版权益包：{@code POST https://api.trae.com.cn/trae/api/v2/pay/ide_user_ent_usage}
 *       （body {@code {"require_usage":true,"req_source":2}}）；</li>
 *   <li>国内版付费状态：{@code POST .../trae/api/v1/pay/ide_user_pay_status}；</li>
 *   <li>国际版：两者换 {@code grow-normal.traeapi.us}，且权益包走 {@code /v1} 路径。</li>
 * </ul>
 *
 * <p>上游 pay 结构里没有统一的「余额」字段，权益包列表是唯一事实来源；因此这里
 * 逐个包累加 {@code remain}/{@code size}，并把明细原样透出（{@code packages}）。
 * 缺失不等于 0：全部字段都没取到时返回 {@code error}，由前端展示「未知」。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("trae")
public class TraeCreditQueryProvider implements CreditQueryProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "trae";

    /**
     * 国内版付费域（与 trae_client.REALMS.cn.pay 一致）
     */
    static final String PAY_CN = "https://api.trae.com.cn";

    /**
     * 国际版付费域
     */
    static final String PAY_INTL = "https://grow-normal.traeapi.us";

    /**
     * 国际版付费域备选
     */
    static final String PAY_INTL_FALLBACK = "https://grow-normal.trae.ai";

    /**
     * 国内版权益包路径
     */
    static final String ENT_USAGE_V2 = "/trae/api/v2/pay/ide_user_ent_usage";

    /**
     * 国际版权益包路径（无 {@code /v2} 段）
     */
    static final String ENT_USAGE_V1 = "/trae/api/v1/pay/ide_user_ent_usage";

    /**
     * 付费状态路径（两域相同）
     */
    static final String PAY_STATUS = "/trae/api/v1/pay/ide_user_pay_status";

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
     * 查询当前账号的积分 / 余额。
     *
     * <p>永不抛异常：任一 pay 接口失败只记 {@code error}，另一个成功仍按可用字段返回。</p>
     *
     * @param credentials 账号凭据（来自连接表 credentials 字段）
     * @return 积分快照；失败时含 {@code error}
     */
    public Map<String, Object> credit(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(10);
        String accessToken = readString(credentials, "access_token");
        String uid = readString(credentials, "uid");
        if (isBlank(accessToken) || isBlank(uid)) {
            result.put("error", "凭据缺少 access_token 或 uid");
            return result;
        }
        boolean intl = isIntl(credentials);
        Map<String, String> headers = payHeaders(accessToken, uid);
        List<String> errors = new ArrayList<>(2);

        Map<String, Object> entitlement = null;
        String entPath = intl ? ENT_USAGE_V1 : ENT_USAGE_V2;
        List<String> hosts = intl ? List.of(PAY_INTL, PAY_INTL_FALLBACK) : List.of(PAY_CN);
        for (String host : hosts) {
            try {
                String body = ChannelHttp.postJson(host + entPath,
                        Json.toJson(Map.of("require_usage", true, "req_source", 2)), headers);
                entitlement = parseJson(body);
                break;
            } catch (Exception e) {
                errors.add("ent_usage@" + host + ": " + ChannelHttp.truncate(e.getMessage()));
            }
        }

        Map<String, Object> payStatus = null;
        try {
            String body = ChannelHttp.postJson(hosts.get(0) + PAY_STATUS,
                    Json.toJson(Map.of()), headers);
            payStatus = parseJson(body);
        } catch (Exception e) {
            errors.add("pay_status: " + ChannelHttp.truncate(e.getMessage()));
        }

        if (entitlement == null && payStatus == null) {
            result.put("error", String.join("; ", errors));
            return result;
        }

        List<Map<String, Object>> packages = new ArrayList<>(4);
        long remain = 0L;
        long total = 0L;
        long expiresAt = 0L;
        for (Map<String, Object> root : rootsOf(entitlement, payStatus)) {
            List<Map<String, Object>> rows = benefitRows(root);
            for (Map<String, Object> row : rows) {
                long rowRemain = longOf(row.get("remain"));
                long rowSize = longOf(row.get("size"));
                remain += Math.max(rowRemain, 0L);
                total += Math.max(rowSize, rowRemain);
                long rowExpires = longOf(row.get("expiresAt"));
                if (rowExpires > 0 && (expiresAt == 0 || rowExpires < expiresAt)) {
                    expiresAt = rowExpires;
                }
                packages.add(row);
            }
        }
        if (packages.isEmpty() && remain == 0L && total == 0L) {
            // 权益包里一个可读字段都没有：如实报未知，不把缺失当 0
            result.put("error", "权益接口未返回可用额度字段"
                    + (errors.isEmpty() ? "" : "（" + String.join("; ", errors) + "）"));
            return result;
        }

        result.put("remain", remain);
        result.put("total", total);
        result.put("unit", "credit");
        result.put("realm", intl ? "intl" : "cn");
        result.put("plan", planOf(entitlement, payStatus));
        result.put("exceeded", remain <= 0 && total > 0);
        result.put("updatedAt", System.currentTimeMillis());
        if (expiresAt > 0) {
            result.put("expiresAt", expiresAt);
        }
        if (!packages.isEmpty()) {
            result.put("packages", packages);
        }
        if (!errors.isEmpty()) {
            result.put("warning", String.join("; ", errors));
        }
        return result;
    }

    // ==================== 解析 ====================

    /**
     * 权益 / 付费状态可能各自套一层信封，按 {@code data} 解包。
     *
     * @param roots 原始响应
     * @return 实际字段根节点
     */
    private static List<Map<String, Object>> rootsOf(Map<String, Object>... roots) {
        List<Map<String, Object>> out = new ArrayList<>(roots.length * 2);
        for (Map<String, Object> root : roots) {
            if (root == null || root.isEmpty()) {
                continue;
            }
            out.add(root);
            if (root.get("data") instanceof Map<?, ?> data) {
                out.add(asMap(data));
            }
            if (root.get("result") instanceof Map<?, ?> result) {
                out.add(asMap(result));
            }
        }
        return out;
    }

    /**
     * 从若干候选根里提取权益包列表（字段名兼容上游多种写法）。
     *
     * @param root 候选根
     * @return 归一化权益包
     */
    private static List<Map<String, Object>> benefitRows(Map<String, Object> root) {
        Object raw = firstValue(root, "benefits", "packages", "entitlements", "items",
                "quotas", "benefitList", "benefitlist", "userEntitlementPackList",
                "user_entitlement_pack_list", "entitlementPackList", "packList", "pack_list");
        List<Map<String, Object>> rows = new ArrayList<>(4);
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> row = normalizeBenefit(item, null);
                if (row != null) {
                    rows.add(row);
                }
            }
        } else if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Map<String, Object> row = normalizeBenefit(entry.getValue(), String.valueOf(entry.getKey()));
                if (row != null) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * 归一化单个权益包。
     *
     * @param item    原始节点
     * @param nameKey 来自 Map 键的名字兜底
     * @return 权益包行；不可解析返回 {@code null}
     */
    private static Map<String, Object> normalizeBenefit(Object item, String nameKey) {
        if (!(item instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> node = asMap(raw);
        Long remain = firstNumber(node, "remain", "remaining", "balance", "credits",
                "credit", "quotaRemain", "quota_remain", "left", "cycleRemain",
                "cycle_remain", "capacityRemain", "capacity_remain");
        Long size = firstNumber(node, "size", "total", "quota", "totalQuota",
                "total_quota", "limit", "cycleSize", "cycle_size", "capacitySize",
                "capacity_size", "limitNum", "limit_num");
        String name = firstString(node, "name", "title", "packageName", "package_name",
                "plan", "planName", "plan_name");
        if (remain == null && size == null && isBlank(name) && isBlank(nameKey)) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>(5);
        row.put("name", isBlank(name) ? (isBlank(nameKey) ? "权益包" : nameKey) : name);
        if (remain != null) {
            row.put("remain", Math.max(remain, 0L));
        }
        if (size != null) {
            row.put("size", Math.max(size, 0L));
        }
        Long expires = firstEpoch(node, "expiresAt", "expires_at", "expireAt", "expire_at",
                "expiredAt", "expired_at", "endTime", "end_time", "endDate", "end_date",
                "cycleEndTime", "cycle_end_time");
        if (expires != null && expires > 0) {
            row.put("expiresAt", expires);
        }
        return row;
    }

    /**
     * 套餐 / 计划名。
     *
     * @param roots 候选根
     * @return 套餐名；未取到返回 {@code null}
     */
    private static String planOf(Map<String, Object>... roots) {
        for (Map<String, Object> root : rootsOf(roots)) {
            String plan = firstString(root, "plan", "planName", "plan_name", "planType",
                    "plan_type", "productName", "product_name", "subscription", "tier",
                    "packageName", "package_name");
            if (!isBlank(plan)) {
                return plan;
            }
        }
        return null;
    }

    // ==================== 请求头 ====================

    /**
     * pay 域请求头（{@code Cloud-IDE-JWT} + 账号派生的设备标识）。
     *
     * @param accessToken 访问令牌
     * @param uid         uid
     * @return 请求头
     */
    private static Map<String, String> payHeaders(String accessToken, String uid) {
        Map<String, String> headers = new LinkedHashMap<>(6);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Cloud-IDE-JWT", accessToken);
        headers.put("User-Agent", "Trae/0.1.52");
        headers.put("X-Device-Id", checkinDeviceId(uid));
        headers.put("X-Market-User-Id", checkinMarketUserId(uid));
        headers.put("X-Session-Id", checkinSessionId(uid));
        return headers;
    }

    /**
     * 是否国际版账号（凭据 {@code realm} 或站点后缀判定）。
     *
     * @param credentials 凭据
     * @return true 表示国际版
     */
    public static boolean isIntl(Map<String, Object> credentials) {
        String realm = readString(credentials, "realm");
        if (!isBlank(realm)) {
            return "intl".equalsIgnoreCase(realm.trim());
        }
        String base = readString(credentials, "base_url");
        if (isBlank(base)) {
            base = readString(credentials, "site");
        }
        return !isBlank(base) && (base.contains("trae.ai") || base.contains("traeapi.us"));
    }

    // ==================== 工具 ====================

    /**
     * 取首个非空字段值。
     *
     * @param node 节点
     * @param keys 候选键
     * @return 值；全空返回 {@code null}
     */
    private static Object firstValue(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            Object value = node.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 取首个可解析为数字的字段。
     *
     * @param node 节点
     * @param keys 候选键
     * @return 数值；全空返回 {@code null}
     */
    private static Long firstNumber(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            Long value = longOrNull(node.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 取首个可解析为时间的字段（秒 / 毫秒 / ISO 串统一归一到毫秒）。
     *
     * @param node 节点
     * @param keys 候选键
     * @return 毫秒时间戳；全空返回 {@code null}
     */
    private static Long firstEpoch(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            Long value = epochOf(node.get(key));
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    /**
     * 取首个非空字符串字段。
     *
     * @param node 节点
     * @param keys 候选键
     * @return 字符串；全空返回 {@code null}
     */
    private static String firstString(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            Object value = node.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
            if (value instanceof Number || value instanceof Boolean) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * 数字归一（支持字符串形态）。
     *
     * @param value 原始值
     * @return 长整数；不可解析返回 {@code null}
     */
    private static Long longOrNull(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                try {
                    return Math.round(Double.parseDouble(text.trim()));
                } catch (NumberFormatException ignoredAgain) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 数字归一（不可解析按 0）。
     *
     * @param value 原始值
     * @return 长整数
     */
    private static long longOf(Object value) {
        Long result = longOrNull(value);
        return result == null ? 0L : result;
    }

    /**
     * 时间归一：&lt; 1e12 视为秒，其余视为毫秒；ISO 串走 {@code Instant}。
     *
     * @param value 原始值
     * @return 毫秒时间戳；不可解析返回 {@code null}
     */
    private static Long epochOf(Object value) {
        Long numeric = longOrNull(value);
        if (numeric != null) {
            return numeric < 1_000_000_000_000L ? numeric * 1000L : numeric;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return java.time.Instant.parse(text.trim()).toEpochMilli();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
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
     * 读取非空字符串。
     *
     * @param source 源 Map
     * @param key    键
     * @return 值；空串返回 {@code null}
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
     * 归一化账号域（供外部按需复用）。
     *
     * @param realm 原始值
     * @return {@code intl} / {@code cn}
     */
    static String normalizeRealm(String realm) {
        if (isBlank(realm)) {
            return "cn";
        }
        String value = realm.trim().toLowerCase(Locale.ROOT);
        return "intl".equals(value) || "global".equals(value) ? "intl" : "cn";
    }

    // ==================== 设备指纹（内联自 TraeAccountChannelAuthManager，方法体原样） ====================

    /**
     * checkin Device Id
     * @param uid 字符串
     * @return 返回值
     */
    private static String checkinDeviceId(String uid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(("trae-checkin-device-" + uid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(15);
            long value = 0;
            for (int i = 0; i < 6; i++) {
                value = (value << 8) | (hash[i] & 0xFF);
            }
            sb.append(value % 10_000_000_000_000_000L);
            return sb.toString();
        } catch (Exception e) {
            return "111111111111111";
        }
    }

    /**
     * checkin Market User Id
     * @param uid 字符串
     * @return 返回值
     */
    private static String checkinMarketUserId(String uid) {
        String seed = "trae-market-user-" + uid;
        byte[] bytes = seed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder hex = new StringBuilder(32);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        String h = hex.substring(0, 32);
        return h.substring(0, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16)
                + "-" + h.substring(16, 20) + "-" + h.substring(20);
    }

    /**
     * checkin Session Id
     * @param uid 字符串
     * @return 返回值
     */
    private static String checkinSessionId(String uid) {
        String seed = "trae-session-" + uid;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }
}
