package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiAppKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * AppKey 的额度闸门：把「这把 Key 现在还能不能用」的全部判定收成一份纯函数。
 *
 * <p>为什么必须收成一份：同一个 Key 有两处以完全不同的代码判同一件事 ——</p>
 * <ul>
 *   <li>网关 {@code /v2/v1/*}：{@code SysAiAppKeyServiceImpl#isValidAppKey}（Spring 线程）；</li>
 *   <li>服务实例端口：{@code ServiceInstanceManager#authenticateAppKey}（裸 HTTP 线程）。</li>
 * </ul>
 * <p>两处各写一遍时已经跑出三处不一致：到期时间一处读 {@code expire_datetime}、一处只读
 * {@code expire_time}；次数与令牌的边界、余额的位置也各不相同。任何一处改动都得记得改另一边，
 * 而这种「记得」迟早会失效，表现为「网关放行、实例拒绝」或反过来的灵异问题。</p>
 *
 * <p>本类只做判定，不做 IO、不写日志、不动数据库 —— 因此可以被逐条覆盖测试；
 * 两处调用方只负责把 {@link Deny} 翻译成各自的协议表达（HTTP 状态码 / SSE 错误帧 / 布尔）。</p>
 *
 * <p>判定顺序是刻意固定的（先便宜后昂贵、先身份后额度）：</p>
 * <ol>
 *   <li>{@link Deny#DISABLED}：Key 不存在或未启用；</li>
 *   <li>{@link Deny#EXPIRED}：到期（按时间配额）；</li>
 *   <li>{@link Deny#USE_EXHAUSTED}：次数用尽（按次数配额）；</li>
 *   <li>{@link Deny#TOKEN_EXHAUSTED}：Token 总量用尽（按余额/总量配额）；</li>
 *   <li>{@link Deny#IP_NOT_ALLOWED}：来源 IP 不在白名单；</li>
 *   <li>{@link Deny#INSUFFICIENT_BALANCE}：余额为负且不允许透支（金额口径，单独一步）。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class AppKeyQuotaGate {

    /**
     * 秒/毫秒的量级分界：{@code 1e11} 毫秒约为 1973 年，{@code 1e11} 秒约为 5138 年。
     *
     * <p>{@code sys_ai_app_key.sys_ai_app_key_expire_time} 在历史库中两种写法都出现过，
     * 按量级判定比按列注释猜更安全：判错一位就是「全部 Key 立刻过期」或「永不过期」。</p>
     */
    private static final long MILLIS_THRESHOLD = 100_000_000_000L;

    /**
     * 启用状态值。
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 工具类不允许实例化。
     */
    private AppKeyQuotaGate() {
    }

    /**
     * 闸门判定结果。
     *
     * <p>刻意不抛异常、不带文案：文案属于各入口的协议表达（OpenAI 错误体、SSE 帧、
     * 实例端口的 {@code {"error":...}}），放在这里会逼着两端说同一句话。</p>
     */
    public enum Deny {

        /**
         * 放行。
         */
        NONE,

        /**
         * Key 不存在 / 未启用。
         */
        DISABLED,

        /**
         * 已到期（按时间配额）。
         */
        EXPIRED,

        /**
         * 请求次数用尽（按次数配额）。
         */
        USE_EXHAUSTED,

        /**
         * Token 总量用尽（按总量配额）。
         */
        TOKEN_EXHAUSTED,

        /**
         * 来源 IP 不在白名单。
         */
        IP_NOT_ALLOWED,

        /**
         * 余额为负且不允许透支（金额口径）。
         */
        INSUFFICIENT_BALANCE;

        /**
         * 是否拒绝放行。
         *
         * @return 非 {@link #NONE} 返回 true
         */
        public boolean denied() {
            return this != NONE;
        }
    }

    /**
     * 判定 Key 自身是否可用（不含余额：余额是计费口径，见 {@link #checkBillable}）。
     *
     * @param key       appKey 行，可为 null（视为不存在）
     * @param clientIp  客户端 IP，可为 null（为 null 时不做白名单判定）
     * @param nowMillis 当前时间戳（毫秒），用于到期判定
     * @return 判定结果，放行返回 {@link Deny#NONE}
     */
    public static Deny check(SysAiAppKey key, String clientIp, long nowMillis) {
        if (key == null) {
            return Deny.DISABLED;
        }
        if (key.getStatus() == null || key.getStatus() != STATUS_ENABLED) {
            return Deny.DISABLED;
        }
        if (expired(key, nowMillis)) {
            return Deny.EXPIRED;
        }
        if (useExhausted(key)) {
            return Deny.USE_EXHAUSTED;
        }
        if (tokensExhausted(key)) {
            return Deny.TOKEN_EXHAUSTED;
        }
        if (!ipAllowed(key, clientIp)) {
            return Deny.IP_NOT_ALLOWED;
        }
        return Deny.NONE;
    }

    /**
     * 用当前时间判定 Key 自身是否可用。
     *
     * @param key      appKey 行，可为 null
     * @param clientIp 客户端 IP，可为 null
     * @return 判定结果
     */
    public static Deny check(SysAiAppKey key, String clientIp) {
        return check(key, clientIp, System.currentTimeMillis());
    }

    /**
     * 计费守卫：余额为负且不允许透支时拒绝发起新请求。
     *
     * <p>{@code overdraftEnabled=1} 时允许透支（余额继续走负，账单照记）；
     * 这与「总量配额」是两件事：前者算钱，后者算 Token。</p>
     *
     * @param key appKey 行，可为 null（沿用旧口径：查不到即不可计量）
     * @return 放行返回 {@link Deny#NONE}，否则 {@link Deny#INSUFFICIENT_BALANCE}
     */
    public static Deny checkBillable(SysAiAppKey key) {
        if (key == null) {
            return Deny.INSUFFICIENT_BALANCE;
        }
        BigDecimal balance = key.getBalance() != null ? key.getBalance() : BigDecimal.ZERO;
        // overdraftEnabled 在库里是 int（1 = 允许透支）。历史实现里网关侧写成
        // Boolean.TRUE.equals(Integer)，恒为 false —— 「允许透支」开关从未生效过，
        // 这里按 int 判定，与实例端口口径一致。
        boolean overdraft = Integer.valueOf(1).equals(key.getOverdraftEnabled());
        if (balance.compareTo(BigDecimal.ZERO) < 0 && !overdraft) {
            return Deny.INSUFFICIENT_BALANCE;
        }
        return Deny.NONE;
    }

    /**
     * 是否已到期。
     *
     * <p>口径：{@code expire_datetime} 为权威（控制台只写这一列）；为空时才回退到老的
     * {@code expire_time} 数值列，并兼容秒 / 毫秒两种量级。两列都为空 = 永不过期。</p>
     *
     * @param key       appKey 行，可为 null
     * @param nowMillis 当前时间戳（毫秒）
     * @return 已到期返回 true
     */
    public static boolean expired(SysAiAppKey key, long nowMillis) {
        if (key == null) {
            return false;
        }
        LocalDateTime datetime = key.getExpireDatetime();
        if (datetime != null) {
            LocalDateTime now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault());
            return datetime.isBefore(now);
        }
        Long raw = key.getExpireTime();
        if (raw == null || raw <= 0) {
            return false;
        }
        long deadline = raw > MILLIS_THRESHOLD ? raw : raw * 1000L;
        return nowMillis > deadline;
    }

    /**
     * 次数配额是否用尽（{@code maxUse} 为空或 {@code <=0} 表示不限）。
     *
     * @param key appKey 行，可为 null
     * @return 用尽返回 true
     */
    public static boolean useExhausted(SysAiAppKey key) {
        if (key == null) {
            return false;
        }
        Long maxUse = key.getMaxUse();
        if (maxUse == null || maxUse <= 0) {
            return false;
        }
        long used = key.getUseCount() == null ? 0L : key.getUseCount();
        return used >= maxUse;
    }

    /**
     * Token 总量配额是否用尽（{@code maxTokens} 为空或 {@code <=0} 表示不限）。
     *
     * @param key appKey 行，可为 null
     * @return 用尽返回 true
     */
    public static boolean tokensExhausted(SysAiAppKey key) {
        if (key == null) {
            return false;
        }
        Long maxTokens = key.getMaxTokens();
        if (maxTokens == null || maxTokens <= 0) {
            return false;
        }
        long used = key.getTotalTokens() == null ? 0L : key.getTotalTokens();
        return used >= maxTokens;
    }

    /**
     * 来源 IP 是否被允许：白名单为空 = 不限制；IP 未知时不判定（不能因为拿不到 IP 就把人挡在门外）。
     *
     * @param key       appKey 行，可为 null
     * @param clientIp  客户端 IP，可为 null 或空白
     * @return 允许返回 true
     */
    public static boolean ipAllowed(SysAiAppKey key, String clientIp) {
        if (key == null) {
            return false;
        }
        String whitelist = key.getIpWhitelist();
        if (whitelist == null || whitelist.isBlank()) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return true;
        }
        for (String part : whitelist.split(",")) {
            String v = part.trim();
            if (v.isEmpty()) {
                continue;
            }
            if ("*".equals(v) || v.equals(clientIp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 剩余可用次数（供台账/日志展示；不限时返回 {@code -1}）。
     *
     * @param key appKey 行，可为 null
     * @return 剩余次数，不限或未知返回 -1
     */
    public static long remainingUse(SysAiAppKey key) {
        if (key == null || key.getMaxUse() == null || key.getMaxUse() <= 0) {
            return -1L;
        }
        long used = key.getUseCount() == null ? 0L : key.getUseCount();
        return Math.max(0L, key.getMaxUse() - used);
    }

    /**
     * 剩余可用 Token（供台账/日志展示；不限时返回 {@code -1}）。
     *
     * @param key appKey 行，可为 null
     * @return 剩余 Token，不限或未知返回 -1
     */
    public static long remainingTokens(SysAiAppKey key) {
        if (key == null || key.getMaxTokens() == null || key.getMaxTokens() <= 0) {
            return -1L;
        }
        long used = key.getTotalTokens() == null ? 0L : key.getTotalTokens();
        return Math.max(0L, key.getMaxTokens() - used);
    }
}
