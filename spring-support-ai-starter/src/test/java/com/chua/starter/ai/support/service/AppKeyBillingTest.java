// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.AiUsage;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.entity.SysAiPricing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AppKeyBilling} 扣费口径测试 —— 「这一句话该扣多少钱」逐条钉死。
 *
 * <p>覆盖范围：</p>
 * <ol>
 *   <li><b>是否计量</b>：余额为 0 / 花过钱 / 充值过 / 开透支 / Key 为 null；</li>
 *   <li><b>金额来源</b>：用量自带 {@code totalCost} 优先；无金额时按每 1k 单价重算；</li>
 *   <li><b>Token 口径</b>：输入 / 输出分别计价，缓存命中按输入 5 折；null 与负数按 0；</li>
 *   <li><b>币种换算</b>：CNY 不换算；USD × 7.1；EUR × 7.8；币种未知按原值；汇率非法按原值；</li>
 *   <li><b>落库精度</b>：6 位小数四舍五入；零金额不扣。</li>
 * </ol>
 *
 * <p>为什么这些断言必须存在：扣费挂在<b>每一轮回复</b>上，算错不会抛异常，
 * 只会悄悄多扣 / 少扣。金额是用户能直接对账的数字，错了就是资损。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@DisplayName("AppKeyBilling 扣费口径")
class AppKeyBillingTest {

    /**
     * 美元汇率（与用量配置默认值一致）。
     */
    private static final BigDecimal RATE_USD = new BigDecimal("7.1");

    /**
     * 欧元汇率。
     */
    private static final BigDecimal RATE_EUR = new BigDecimal("7.8");

    /**
     * 构造一个只有令牌数与费用的用量。
     *
     * @param input    输入令牌
     * @param output   输出令牌
     * @param cache    缓存令牌，可为 null
     * @param currency 币种
     * @return 用量
     */
    private static AiUsage usage(Integer input, Integer output, Integer cache, String currency) {
        return AiUsage.builder()
                .inputTokens(input)
                .outputTokens(output)
                .cacheTokens(cache)
                .currency(currency)
                .build();
    }

    /**
     * 构造一份每 1k 令牌单价给定、币种给定的定价。
     *
     * @param input    输入单价（每 1k）
     * @param output   输出单价（每 1k）
     * @param currency 币种
     * @return 定价
     */
    private static SysAiPricing pricing(Double input, Double output, String currency) {
        SysAiPricing pricing = new SysAiPricing();
        pricing.setInputPricePer1k(input);
        pricing.setOutputPricePer1k(output);
        pricing.setCurrency(currency);
        return pricing;
    }

    @Nested
    @DisplayName("是否进入预付计量")
    class Metered {

        @Test
        @DisplayName("Key 为 null 不计量")
        void nullKey() {
            assertFalse(AppKeyBilling.metered(null));
        }

        @Test
        @DisplayName("余额 0 且从未花过钱 = 免计量（靠次数/总量配额）")
        void zeroBalanceNeverSpent() {
            SysAiAppKey key = new SysAiAppKey();
            key.setBalance(BigDecimal.ZERO);
            key.setTotalCost(BigDecimal.ZERO);
            key.setOverdraftEnabled(0);
            assertFalse(AppKeyBilling.metered(key));
        }

        @Test
        @DisplayName("余额全为 null 视为 0，不计量")
        void nullBalanceAndCost() {
            assertFalse(AppKeyBilling.metered(new SysAiAppKey()));
        }

        @Test
        @DisplayName("余额为正 = 已充值，计量")
        void positiveBalance() {
            SysAiAppKey key = new SysAiAppKey();
            key.setBalance(new BigDecimal("10.00"));
            assertTrue(AppKeyBilling.metered(key));
        }

        @Test
        @DisplayName("余额被扣到 0 但累计费用为正 = 花过钱，继续计量")
        void spentBefore() {
            SysAiAppKey key = new SysAiAppKey();
            key.setBalance(BigDecimal.ZERO);
            key.setTotalCost(new BigDecimal("0.05"));
            assertTrue(AppKeyBilling.metered(key));
        }

        @Test
        @DisplayName("开了透支 = 计量（余额可以走负）")
        void overdraft() {
            SysAiAppKey key = new SysAiAppKey();
            key.setBalance(BigDecimal.ZERO);
            key.setOverdraftEnabled(1);
            assertTrue(AppKeyBilling.metered(key));
        }
    }

    @Nested
    @DisplayName("金额来源")
    class Amount {

        @Test
        @DisplayName("用量自带 totalCost 优先于定价重算")
        void usageCostWins() {
            AiUsage usage = usage(1000, 1000, null, "USD");
            usage.setTotalCost(new BigDecimal("1.23"));
            AppKeyBilling.Charge charge = AppKeyBilling.chargeOf(usage,
                    pricing(999.0, 999.0, "CNY"));
            assertEquals(0, charge.amount().compareTo(new BigDecimal("1.23")));
            assertEquals("USD", charge.currency());
        }

        @Test
        @DisplayName("用量无金额时按每 1k 单价重算")
        void pricingFallback() {
            // 输入 1000 tok × 0.0015/1k = 0.0015；输出 500 tok × 0.006/1k = 0.003
            AppKeyBilling.Charge charge = AppKeyBilling.chargeOf(usage(1000, 500, null, "CNY"),
                    pricing(0.0015, 0.006, "CNY"));
            assertEquals(0, charge.amount().compareTo(new BigDecimal("0.0045")));
        }

        @Test
        @DisplayName("无用量 / 无定价 → 零金额")
        void noUsageOrPricing() {
            assertFalse(AppKeyBilling.chargeOf(null, pricing(1.0, 1.0, "CNY")).positive());
            assertFalse(AppKeyBilling.chargeOf(usage(10, 10, null, "CNY"), null).positive());
        }

        @Test
        @DisplayName("定价单价两列全空 → 零金额（不猜价）")
        void pricingWithoutPrices() {
            assertFalse(AppKeyBilling.chargeOf(usage(10, 10, null, "CNY"),
                    pricing(null, null, "CNY")).positive());
        }

        @Test
        @DisplayName("只有输入单价时只计输入，输出不按 0 记")
        void onlyInputPrice() {
            AppKeyBilling.Charge charge = AppKeyBilling.chargeOf(usage(1000, 1000, null, "CNY"),
                    pricing(0.01, null, "CNY"));
            assertEquals(0, charge.amount().compareTo(new BigDecimal("0.01")));
        }

        @Test
        @DisplayName("usageCost：未带金额时返回零")
        void usageCostZero() {
            assertFalse(AppKeyBilling.usageCost(usage(10, 10, null, "CNY")).positive());
            assertFalse(AppKeyBilling.usageCost(null).positive());
        }

        @Test
        @DisplayName("usageCost：带金额时原样返回（含币种）")
        void usageCostPassThrough() {
            AiUsage usage = usage(1, 1, null, "EUR");
            usage.setTotalCost(new BigDecimal("0.5"));
            AppKeyBilling.Charge charge = AppKeyBilling.usageCost(usage);
            assertEquals(0, charge.amount().compareTo(new BigDecimal("0.5")));
            assertEquals("EUR", charge.currency());
        }
    }

    @Nested
    @DisplayName("令牌口径")
    class Tokens {

        @Test
        @DisplayName("缓存命中按输入单价 5 折单独计价")
        void cacheDiscounted() {
            // 输入 0 + 缓存 1000 × 0.002 × 0.5 = 0.001
            AppKeyBilling.Charge charge = AppKeyBilling.chargeOf(usage(0, 0, 1000, "CNY"),
                    pricing(0.002, 0.002, "CNY"));
            assertEquals(0, charge.amount().compareTo(new BigDecimal("0.001")));
        }

        @Test
        @DisplayName("令牌字段为 null / 负数一律按 0，不产生负金额")
        void nullAndNegativeTokens() {
            AppKeyBilling.Charge charge = AppKeyBilling.chargeOf(usage(null, -100, -5, "CNY"),
                    pricing(1.0, 1.0, "CNY"));
            assertEquals(0, charge.amount().compareTo(BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("币种换算")
    class Currency {

        @Test
        @DisplayName("人民币不换算")
        void cnyNoConversion() {
            BigDecimal cny = AppKeyBilling.toCny(
                    new AppKeyBilling.Charge(new BigDecimal("2"), "CNY"), RATE_USD, RATE_EUR);
            assertEquals(0, cny.compareTo(new BigDecimal("2.000000")));
        }

        @Test
        @DisplayName("美元按 7.1 折算")
        void usdConverted() {
            BigDecimal cny = AppKeyBilling.toCny(
                    new AppKeyBilling.Charge(new BigDecimal("1"), "USD"), RATE_USD, RATE_EUR);
            assertEquals(0, cny.compareTo(new BigDecimal("7.100000")));
        }

        @Test
        @DisplayName("欧元按 7.8 折算")
        void eurConverted() {
            BigDecimal cny = AppKeyBilling.toCny(
                    new AppKeyBilling.Charge(new BigDecimal("1"), "EUR"), RATE_USD, RATE_EUR);
            assertEquals(0, cny.compareTo(new BigDecimal("7.800000")));
        }

        @Test
        @DisplayName("币种未知 / 为 null 时按原值，不猜汇率")
        void unknownCurrency() {
            assertEquals(0, AppKeyBilling.toCny(
                            new AppKeyBilling.Charge(new BigDecimal("3"), "JPY"), RATE_USD, RATE_EUR)
                    .compareTo(new BigDecimal("3.000000")));
            assertEquals(0, AppKeyBilling.toCny(
                            new AppKeyBilling.Charge(new BigDecimal("3"), null), RATE_USD, RATE_EUR)
                    .compareTo(new BigDecimal("3.000000")));
        }

        @Test
        @DisplayName("汇率缺失或非正数时按原值")
        void invalidRate() {
            assertEquals(0, AppKeyBilling.toCny(
                            new AppKeyBilling.Charge(new BigDecimal("2"), "USD"), null, RATE_EUR)
                    .compareTo(new BigDecimal("2.000000")));
            assertEquals(0, AppKeyBilling.toCny(
                            new AppKeyBilling.Charge(new BigDecimal("2"), "USD"), BigDecimal.ZERO, RATE_EUR)
                    .compareTo(new BigDecimal("2.000000")));
        }

        @Test
        @DisplayName("小额美元折算后保留 6 位小数")
        void smallAmountScale() {
            // 0.0001234 USD × 7.1 = 0.00087614 → 0.000876
            BigDecimal cny = AppKeyBilling.toCny(
                    new AppKeyBilling.Charge(new BigDecimal("0.0001234"), "USD"), RATE_USD, RATE_EUR);
            assertEquals(new BigDecimal("0.000876"), cny);
        }

        @Test
        @DisplayName("零金额 / 空费用折算为 0")
        void zeroCharge() {
            assertEquals(BigDecimal.ZERO, AppKeyBilling.toCny(null, RATE_USD, RATE_EUR));
            assertEquals(BigDecimal.ZERO, AppKeyBilling.toCny(AppKeyBilling.Charge.ZERO, RATE_USD, RATE_EUR));
        }

        @Test
        @DisplayName("normalize：null → 0.000000，超精度四舍五入")
        void normalizeAmount() {
            assertEquals(new BigDecimal("0.000000"), AppKeyBilling.normalize(null));
            assertEquals(new BigDecimal("1.234568"), AppKeyBilling.normalize(new BigDecimal("1.2345675")));
        }
    }
}
