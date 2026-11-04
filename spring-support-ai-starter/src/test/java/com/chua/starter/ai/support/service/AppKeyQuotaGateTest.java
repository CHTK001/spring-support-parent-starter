// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiAppKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AppKeyQuotaGate} 逐条覆盖测试 —— Key 的每一种配额类型都在这里钉死。
 *
 * <p>覆盖范围（每一格都是断言，不靠人肉回归）：</p>
 * <ol>
 *   <li><b>按状态</b>：status 为 null / 0 / 1；</li>
 *   <li><b>按时间</b>：{@code expireDatetime} 过去 / 未来；{@code expireTime} 秒 / 毫秒；两列优先级；</li>
 *   <li><b>按次数</b>：{@code maxUse} null / 0 / 未到 / 正好 / 超出；</li>
 *   <li><b>按总量</b>：{@code maxTokens} null / 0 / 未到 / 正好 / 超出；</li>
 *   <li><b>按 IP</b>：白名单空 / {@code *} / 命中 / 不命中 / 多值 / IP 未知；</li>
 *   <li><b>按余额</b>：余额 null / 0 / 负 + 允许透支 / 负 + 不允许透支 / 正；</li>
 *   <li><b>判定顺序</b>：同时违反多条时先报哪一条（身份 &gt; 到期 &gt; 次数 &gt; 总量 &gt; IP）。</li>
 * </ol>
 *
 * <p>为什么这个测试必须存在：同一个 Key 由<b>两处</b>代码判定（网关 {@code SysAiAppKeyServiceImpl}
 * 与服务实例端口 {@code ServiceInstanceManager}）。两处各写一遍时已经跑出三处不一致
 * （到期列读错、Token 总量不累计、透支开关恒 false）。把判定收成纯函数后，只要这份测试绿，
 * 两个入口的「能不能用」就是同一件事。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@DisplayName("AppKeyQuotaGate 配额闸门")
class AppKeyQuotaGateTest {

    /**
     * 一个「处处放行」的基准 Key：后续每个用例只改一个字段，保证失败点唯一。
     *
     * @return 有效 key
     */
    private static SysAiAppKey healthy() {
        SysAiAppKey key = new SysAiAppKey();
        key.setAppKey("sk-healthy");
        key.setStatus(1);
        return key;
    }

    /**
     * 当前毫秒时间戳。
     *
     * @return now
     */
    private static long now() {
        return System.currentTimeMillis();
    }

    @Nested
    @DisplayName("按状态")
    class Status {

        @Test
        @DisplayName("Key 不存在 → DISABLED")
        void nullKey() {
            assertEquals(AppKeyQuotaGate.Deny.DISABLED, AppKeyQuotaGate.check(null, "1.1.1.1"));
        }

        @Test
        @DisplayName("status 为 null → DISABLED（不许当启用）")
        void nullStatus() {
            SysAiAppKey key = healthy();
            key.setStatus(null);
            assertEquals(AppKeyQuotaGate.Deny.DISABLED, AppKeyQuotaGate.check(key, "1.1.1.1"));
        }

        @Test
        @DisplayName("status = 0 → DISABLED")
        void disabled() {
            SysAiAppKey key = healthy();
            key.setStatus(0);
            assertEquals(AppKeyQuotaGate.Deny.DISABLED, AppKeyQuotaGate.check(key, "1.1.1.1"));
        }

        @Test
        @DisplayName("status = 1 且无其他限制 → NONE")
        void enabled() {
            assertEquals(AppKeyQuotaGate.Deny.NONE, AppKeyQuotaGate.check(healthy(), "1.1.1.1"));
        }

        @Test
        @DisplayName("denied() 对 NONE 为 false，其余为 true")
        void deniedFlag() {
            assertFalse(AppKeyQuotaGate.Deny.NONE.denied());
            assertTrue(AppKeyQuotaGate.Deny.DISABLED.denied());
            assertTrue(AppKeyQuotaGate.Deny.EXPIRED.denied());
            assertTrue(AppKeyQuotaGate.Deny.USE_EXHAUSTED.denied());
            assertTrue(AppKeyQuotaGate.Deny.TOKEN_EXHAUSTED.denied());
            assertTrue(AppKeyQuotaGate.Deny.IP_NOT_ALLOWED.denied());
            assertTrue(AppKeyQuotaGate.Deny.INSUFFICIENT_BALANCE.denied());
        }
    }

    @Nested
    @DisplayName("按时间")
    class Expiry {

        @Test
        @DisplayName("expireDatetime 已过 → EXPIRED")
        void datetimePast() {
            SysAiAppKey key = healthy();
            key.setExpireDatetime(LocalDateTime.now().minusMinutes(1));
            assertEquals(AppKeyQuotaGate.Deny.EXPIRED, AppKeyQuotaGate.check(key, "1.1.1.1"));
        }

        @Test
        @DisplayName("expireDatetime 未来 → NONE")
        void datetimeFuture() {
            SysAiAppKey key = healthy();
            key.setExpireDatetime(LocalDateTime.now().plusDays(1));
            assertEquals(AppKeyQuotaGate.Deny.NONE, AppKeyQuotaGate.check(key, "1.1.1.1"));
        }

        @Test
        @DisplayName("两列都空 → 永不过期")
        void noExpiry() {
            assertFalse(AppKeyQuotaGate.expired(healthy(), now()));
        }

        @Test
        @DisplayName("expireTime 按「秒」写入（过去 → EXPIRED，未来 → NONE）")
        void expireTimeSeconds() {
            long nowMillis = 1_700_000_000_000L;
            long nowSeconds = nowMillis / 1000L;

            SysAiAppKey past = healthy();
            past.setExpireTime(nowSeconds - 60L);
            assertTrue(AppKeyQuotaGate.expired(past, nowMillis));

            SysAiAppKey future = healthy();
            future.setExpireTime(nowSeconds + 60L);
            assertFalse(AppKeyQuotaGate.expired(future, nowMillis));
        }

        @Test
        @DisplayName("expireTime 按「毫秒」写入（过去 → EXPIRED，未来 → NONE）")
        void expireTimeMillis() {
            long nowMillis = 1_700_000_000_000L;

            SysAiAppKey past = healthy();
            past.setExpireTime(nowMillis - 60_000L);
            assertTrue(AppKeyQuotaGate.expired(past, nowMillis));

            SysAiAppKey future = healthy();
            future.setExpireTime(nowMillis + 60_000L);
            assertFalse(AppKeyQuotaGate.expired(future, nowMillis));
        }

        @Test
        @DisplayName("expireTime <= 0 → 视为不限制")
        void expireTimeNonPositive() {
            SysAiAppKey zero = healthy();
            zero.setExpireTime(0L);
            assertFalse(AppKeyQuotaGate.expired(zero, now()));

            SysAiAppKey negative = healthy();
            negative.setExpireTime(-1L);
            assertFalse(AppKeyQuotaGate.expired(negative, now()));
        }

        @Test
        @DisplayName("expireDatetime 优先于 expireTime：datetime 未到 + expireTime 已过 → 放行")
        void datetimeWinsWhenFuture() {
            SysAiAppKey key = healthy();
            key.setExpireDatetime(LocalDateTime.now().plusDays(1));
            key.setExpireTime(1L);
            assertFalse(AppKeyQuotaGate.expired(key, now()));
        }

        @Test
        @DisplayName("expireDatetime 优先于 expireTime：datetime 已过 + expireTime 未来 → 拒绝")
        void datetimeWinsWhenPast() {
            SysAiAppKey key = healthy();
            key.setExpireDatetime(LocalDateTime.now().minusDays(1));
            key.setExpireTime(System.currentTimeMillis() + 86_400_000L);
            assertTrue(AppKeyQuotaGate.expired(key, now()));
        }

        @Test
        @DisplayName("控制台只写 expireDatetime —— 网关曾经只读 expireTime，等于永不过期")
        void consoleWritesDatetimeOnly() {
            SysAiAppKey key = healthy();
            key.setExpireDatetime(LocalDateTime.now().minusSeconds(1));
            key.setExpireTime(null);
            assertEquals(AppKeyQuotaGate.Deny.EXPIRED, AppKeyQuotaGate.check(key, null));
        }
    }

    @Nested
    @DisplayName("按次数")
    class UseQuota {

        @Test
        @DisplayName("maxUse 为 null / 0 / 负 → 不限制")
        void unlimited() {
            SysAiAppKey nullMax = healthy();
            nullMax.setUseCount(999L);
            assertFalse(AppKeyQuotaGate.useExhausted(nullMax));

            SysAiAppKey zeroMax = healthy();
            zeroMax.setMaxUse(0L);
            zeroMax.setUseCount(999L);
            assertFalse(AppKeyQuotaGate.useExhausted(zeroMax));

            SysAiAppKey negativeMax = healthy();
            negativeMax.setMaxUse(-5L);
            negativeMax.setUseCount(999L);
            assertFalse(AppKeyQuotaGate.useExhausted(negativeMax));
        }

        @Test
        @DisplayName("未到上限 → 放行")
        void underLimit() {
            SysAiAppKey key = healthy();
            key.setMaxUse(10L);
            key.setUseCount(9L);
            assertFalse(AppKeyQuotaGate.useExhausted(key));
            assertEquals(AppKeyQuotaGate.Deny.NONE, AppKeyQuotaGate.check(key, null));
        }

        @Test
        @DisplayName("正好等于上限 → USE_EXHAUSTED（>= 边界）")
        void exactlyAtLimit() {
            SysAiAppKey key = healthy();
            key.setMaxUse(10L);
            key.setUseCount(10L);
            assertTrue(AppKeyQuotaGate.useExhausted(key));
            assertEquals(AppKeyQuotaGate.Deny.USE_EXHAUSTED, AppKeyQuotaGate.check(key, null));
        }

        @Test
        @DisplayName("超过上限 → USE_EXHAUSTED")
        void overLimit() {
            SysAiAppKey key = healthy();
            key.setMaxUse(10L);
            key.setUseCount(11L);
            assertEquals(AppKeyQuotaGate.Deny.USE_EXHAUSTED, AppKeyQuotaGate.check(key, null));
        }

        @Test
        @DisplayName("useCount 为 null 视为 0 → 未用尽")
        void nullUseCount() {
            SysAiAppKey key = healthy();
            key.setMaxUse(1L);
            key.setUseCount(null);
            assertFalse(AppKeyQuotaGate.useExhausted(key));
        }

        @Test
        @DisplayName("剩余次数：不限 -1；正常递减；超用不为负")
        void remaining() {
            assertEquals(-1L, AppKeyQuotaGate.remainingUse(healthy()));

            SysAiAppKey key = healthy();
            key.setMaxUse(10L);
            key.setUseCount(3L);
            assertEquals(7L, AppKeyQuotaGate.remainingUse(key));

            key.setUseCount(12L);
            assertEquals(0L, AppKeyQuotaGate.remainingUse(key));
        }
    }

    @Nested
    @DisplayName("按 Token 总量")
    class TokenQuota {

        @Test
        @DisplayName("maxTokens 为 null / 0 → 不限制")
        void unlimited() {
            SysAiAppKey nullMax = healthy();
            nullMax.setTotalTokens(1_000_000L);
            assertFalse(AppKeyQuotaGate.tokensExhausted(nullMax));

            SysAiAppKey zeroMax = healthy();
            zeroMax.setMaxTokens(0L);
            zeroMax.setTotalTokens(1_000_000L);
            assertFalse(AppKeyQuotaGate.tokensExhausted(zeroMax));
        }

        @Test
        @DisplayName("未到上限 → 放行")
        void underLimit() {
            SysAiAppKey key = healthy();
            key.setMaxTokens(1000L);
            key.setTotalTokens(999L);
            assertFalse(AppKeyQuotaGate.tokensExhausted(key));
        }

        @Test
        @DisplayName("正好等于 / 超过上限 → TOKEN_EXHAUSTED")
        void exhausted() {
            SysAiAppKey atLimit = healthy();
            atLimit.setMaxTokens(1000L);
            atLimit.setTotalTokens(1000L);
            assertEquals(AppKeyQuotaGate.Deny.TOKEN_EXHAUSTED, AppKeyQuotaGate.check(atLimit, null));

            SysAiAppKey over = healthy();
            over.setMaxTokens(1000L);
            over.setTotalTokens(1001L);
            assertEquals(AppKeyQuotaGate.Deny.TOKEN_EXHAUSTED, AppKeyQuotaGate.check(over, null));
        }

        @Test
        @DisplayName("totalTokens 为 null 视为 0 → 未用尽")
        void nullTotal() {
            SysAiAppKey key = healthy();
            key.setMaxTokens(1000L);
            key.setTotalTokens(null);
            assertFalse(AppKeyQuotaGate.tokensExhausted(key));
        }

        @Test
        @DisplayName("剩余 Token：不限 -1；正常递减；超用不为负")
        void remaining() {
            assertEquals(-1L, AppKeyQuotaGate.remainingTokens(healthy()));

            SysAiAppKey key = healthy();
            key.setMaxTokens(1000L);
            key.setTotalTokens(400L);
            assertEquals(600L, AppKeyQuotaGate.remainingTokens(key));

            key.setTotalTokens(1200L);
            assertEquals(0L, AppKeyQuotaGate.remainingTokens(key));
        }
    }

    @Nested
    @DisplayName("按来源 IP")
    class IpWhitelist {

        @Test
        @DisplayName("白名单为空 / 空白 → 不限制")
        void noWhitelist() {
            assertTrue(AppKeyQuotaGate.ipAllowed(healthy(), "8.8.8.8"));

            SysAiAppKey blank = healthy();
            blank.setIpWhitelist("   ");
            assertTrue(AppKeyQuotaGate.ipAllowed(blank, "8.8.8.8"));
        }

        @Test
        @DisplayName("通配 * → 全部允许")
        void wildcard() {
            SysAiAppKey key = healthy();
            key.setIpWhitelist("*");
            assertTrue(AppKeyQuotaGate.ipAllowed(key, "8.8.8.8"));
            assertTrue(AppKeyQuotaGate.ipAllowed(key, "1.1.1.1"));
        }

        @Test
        @DisplayName("命中白名单 → 允许（含多值、空白、空段）")
        void hit() {
            SysAiAppKey key = healthy();
            key.setIpWhitelist(" 10.0.0.1 , ,10.0.0.2");
            assertTrue(AppKeyQuotaGate.ipAllowed(key, "10.0.0.2"));
            assertTrue(AppKeyQuotaGate.ipAllowed(key, "10.0.0.1"));
        }

        @Test
        @DisplayName("不在白名单 → IP_NOT_ALLOWED")
        void miss() {
            SysAiAppKey key = healthy();
            key.setIpWhitelist("10.0.0.1,10.0.0.2");
            assertFalse(AppKeyQuotaGate.ipAllowed(key, "8.8.8.8"));
            assertEquals(AppKeyQuotaGate.Deny.IP_NOT_ALLOWED, AppKeyQuotaGate.check(key, "8.8.8.8"));
        }

        @Test
        @DisplayName("客户端 IP 未知（null / 空白）→ 不做判定，不能因取不到 IP 就拒服务")
        void unknownClientIp() {
            SysAiAppKey key = healthy();
            key.setIpWhitelist("10.0.0.1");
            assertTrue(AppKeyQuotaGate.ipAllowed(key, null));
            assertTrue(AppKeyQuotaGate.ipAllowed(key, "  "));
            assertEquals(AppKeyQuotaGate.Deny.NONE, AppKeyQuotaGate.check(key, null));
        }

        @Test
        @DisplayName("多值里带通配 → 命中")
        void wildcardAmongValues() {
            SysAiAppKey key = healthy();
            key.setIpWhitelist("10.0.0.1,*");
            assertTrue(AppKeyQuotaGate.ipAllowed(key, "8.8.8.8"));
        }

        @Test
        @DisplayName("Key 为 null → 不允许")
        void nullKey() {
            assertFalse(AppKeyQuotaGate.ipAllowed(null, "8.8.8.8"));
        }
    }

    @Nested
    @DisplayName("按余额（金额口径）")
    class Balance {

        @Test
        @DisplayName("Key 不存在 → 不可计费")
        void nullKey() {
            assertEquals(AppKeyQuotaGate.Deny.INSUFFICIENT_BALANCE, AppKeyQuotaGate.checkBillable(null));
        }

        @Test
        @DisplayName("余额 null / 0 / 正数 → 放行")
        void enough() {
            assertEquals(AppKeyQuotaGate.Deny.NONE, AppKeyQuotaGate.checkBillable(healthy()));

            SysAiAppKey zero = healthy();
            zero.setBalance(BigDecimal.ZERO);
            assertEquals(AppKeyQuotaGate.Deny.NONE, AppKeyQuotaGate.checkBillable(zero));

            SysAiAppKey positive = healthy();
            positive.setBalance(new BigDecimal("12.50"));
            assertEquals(AppKeyQuotaGate.Deny.NONE, AppKeyQuotaGate.checkBillable(positive));
        }

        @Test
        @DisplayName("余额为负 + 未开透支 → INSUFFICIENT_BALANCE")
        void negativeNoOverdraft() {
            SysAiAppKey key = healthy();
            key.setBalance(new BigDecimal("-0.01"));
            assertEquals(AppKeyQuotaGate.Deny.INSUFFICIENT_BALANCE, AppKeyQuotaGate.checkBillable(key));
        }

        @Test
        @DisplayName("余额为负 + overdraftEnabled = 1 → 允许透支")
        void negativeWithOverdraft() {
            SysAiAppKey key = healthy();
            key.setBalance(new BigDecimal("-100"));
            key.setOverdraftEnabled(1);
            assertEquals(AppKeyQuotaGate.Deny.NONE, AppKeyQuotaGate.checkBillable(key));
        }

        @Test
        @DisplayName("余额为负 + overdraftEnabled = 0 / null → 拒绝（历史实现里 Boolean.TRUE.equals(Integer) 恒 false，开关从未生效）")
        void negativeOverdraftOff() {
            SysAiAppKey off = healthy();
            off.setBalance(new BigDecimal("-1"));
            off.setOverdraftEnabled(0);
            assertEquals(AppKeyQuotaGate.Deny.INSUFFICIENT_BALANCE, AppKeyQuotaGate.checkBillable(off));

            SysAiAppKey nul = healthy();
            nul.setBalance(new BigDecimal("-1"));
            nul.setOverdraftEnabled(null);
            assertEquals(AppKeyQuotaGate.Deny.INSUFFICIENT_BALANCE, AppKeyQuotaGate.checkBillable(nul));
        }

        @Test
        @DisplayName("余额为负但 Key 已停用：身份判定先于余额，报 DISABLED")
        void disabledFirst() {
            SysAiAppKey key = healthy();
            key.setStatus(0);
            key.setBalance(new BigDecimal("-1"));
            assertEquals(AppKeyQuotaGate.Deny.DISABLED, AppKeyQuotaGate.check(key, null));
        }
    }

    @Nested
    @DisplayName("判定顺序（同时违反多条时报第一条）")
    class Order {

        @Test
        @DisplayName("已过期 + 次数用尽 + IP 不匹配 → EXPIRED")
        void expiredFirst() {
            SysAiAppKey key = healthy();
            key.setExpireDatetime(LocalDateTime.now().minusMinutes(1));
            key.setMaxUse(1L);
            key.setUseCount(1L);
            key.setIpWhitelist("10.0.0.1");
            assertEquals(AppKeyQuotaGate.Deny.EXPIRED, AppKeyQuotaGate.check(key, "8.8.8.8"));
        }

        @Test
        @DisplayName("次数用尽 + 总量用尽 + IP 不匹配 → USE_EXHAUSTED")
        void useBeforeTokens() {
            SysAiAppKey key = healthy();
            key.setMaxUse(1L);
            key.setUseCount(1L);
            key.setMaxTokens(1L);
            key.setTotalTokens(1L);
            key.setIpWhitelist("10.0.0.1");
            assertEquals(AppKeyQuotaGate.Deny.USE_EXHAUSTED, AppKeyQuotaGate.check(key, "8.8.8.8"));
        }

        @Test
        @DisplayName("总量用尽 + IP 不匹配 → TOKEN_EXHAUSTED")
        void tokensBeforeIp() {
            SysAiAppKey key = healthy();
            key.setMaxTokens(1L);
            key.setTotalTokens(1L);
            key.setIpWhitelist("10.0.0.1");
            assertEquals(AppKeyQuotaGate.Deny.TOKEN_EXHAUSTED, AppKeyQuotaGate.check(key, "8.8.8.8"));
        }

        @Test
        @DisplayName("全部限制都通过 + 余额为负不透支 → check 放行、checkBillable 拒绝（两步分开）")
        void billableIsSecondStep() {
            SysAiAppKey key = healthy();
            key.setMaxUse(5L);
            key.setUseCount(1L);
            key.setMaxTokens(500L);
            key.setTotalTokens(10L);
            key.setIpWhitelist("10.0.0.1");
            key.setBalance(new BigDecimal("-0.5"));
            assertEquals(AppKeyQuotaGate.Deny.NONE, AppKeyQuotaGate.check(key, "10.0.0.1"));
            assertEquals(AppKeyQuotaGate.Deny.INSUFFICIENT_BALANCE, AppKeyQuotaGate.checkBillable(key));
        }
    }
}
