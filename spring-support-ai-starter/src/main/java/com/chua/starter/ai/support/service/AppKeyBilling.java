package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.AiUsage;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.entity.SysAiPricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * AppKey 计费的纯计算规则：本次调用该扣多少钱、原币种是什么、折成人民币是多少。
 *
 * <p>为什么单独抽出来：扣费一旦写进对话门面，**每一笔回复**都会走一遍，
 * 而金额算错不会报错、只会悄悄多扣或少扣。把「算钱」和「扣钱」拆开，
 * 金额部分就能逐条断言（{@code AppKeyBillingTest}），扣钱部分只负责落库。</p>
 *
 * <p>口径与 {@code SysAiUsageServiceImpl#record} 保持一致，理由是对账：
 * 用量明细表里那一笔 {@code total_cost} 和 Key 台账里扣掉的金额必须是同一个数，
 * 否则用户拿两张表一对就会发现「明细说花了 1 分，余额少了 8 分」。</p>
 *
 * <ul>
 *   <li>单价来源 {@code sys_ai_pricing} 的单位是「每 1k 令牌」；</li>
 *   <li>缓存命中令牌按输入单价的 5 折单独计价（与用量明细同口径）；</li>
 *   <li>余额列是「元」（{@code DECIMAL(12,6)}），USD / EUR 定价按配置汇率折算。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class AppKeyBilling {

    /**
     * 单价的计价单位：每 1k 令牌。
     */
    private static final BigDecimal TOKENS_PER_PRICE_UNIT = BigDecimal.valueOf(1000);

    /**
     * 缓存命中令牌的折扣系数（输入单价的 5 折）。
     */
    private static final BigDecimal CACHE_PRICE_FACTOR = new BigDecimal("0.5");

    /**
     * 计算中间精度：先算准再折算，避免每步都四舍五入放大误差。
     */
    private static final int CALC_SCALE = 10;

    /**
     * 落库精度，与 {@code sys_ai_app_key_balance DECIMAL(12,6)} 对齐。
     */
    public static final int AMOUNT_SCALE = 6;

    /**
     * 工具类不允许实例化。
     */
    private AppKeyBilling() {
    }

    /**
     * 一次调用的费用（原币种，未折算）。
     *
     * @param amount   金额，非负
     * @param currency 币种（CNY / USD / EUR 等），可为空
     */
    public record Charge(BigDecimal amount, String currency) {

        /**
         * 零金额（人民币）。
         */
        public static final Charge ZERO = new Charge(BigDecimal.ZERO, "CNY");

        /**
         * 金额是否大于零。
         *
         * @return 大于零返回 true
         */
        public boolean positive() {
            return amount != null && amount.signum() > 0;
        }
    }

    /**
     * 该 Key 是否已启用「预付余额」计量。
     *
     * <p>为什么需要这道闸：所有历史 Key 的余额默认都是 0、透支默认关，
     * 一旦无条件扣费，第一笔调用就会把余额打成负数，而计费守卫按
     * 「余额 &lt; 0 且不允许透支 → 402」判定 —— 等于把全站 Key 在第一句话之后
     * 全部锁死。只有真正进入预付模式的 Key 才扣：
     * <ul>
     *   <li>余额 &gt; 0：充值过，正在花钱；</li>
     *   <li>累计费用 &gt; 0：曾经花过钱（余额刚被扣到 0），必须继续扣才能走到负数被拦；</li>
     *   <li>允许透支：管理员显式打开了赊账。</li>
     * </ul>
     * 三者都不满足 = 免计量 Key（靠次数 / Token 总量配额管控），不写流水也不动余额。</p>
     *
     * @param key appKey 行，可为 null
     * @return 需要扣费返回 true
     */
    public static boolean metered(SysAiAppKey key) {
        if (key == null) {
            return false;
        }
        if (Integer.valueOf(1).equals(key.getOverdraftEnabled())) {
            return true;
        }
        return positive(key.getBalance()) || positive(key.getTotalCost());
    }

    /**
     * 服务商 / 拦截器已算好的费用（本次调用的权威金额）。
     *
     * @param usage 本次用量，可为 null
     * @return 费用；{@code usage} 未带金额时返回 {@link Charge#ZERO}
     */
    public static Charge usageCost(AiUsage usage) {
        if (usage == null || usage.getTotalCost() == null || usage.getTotalCost().signum() <= 0) {
            return Charge.ZERO;
        }
        return new Charge(usage.getTotalCost(), usage.getCurrency());
    }

    /**
     * 计算一次调用的费用。
     *
     * <p>取价顺序：服务商 / 拦截器已算好的 {@code AiUsage#totalCost} 优先（那是本次调用的
     * 权威金额），缺失时按 {@code sys_ai_pricing} 单价重算。</p>
     *
     * @param usage   本次用量，可为 null
     * @param pricing 定价记录，可为 null（无定价时不扣费）
     * @return 费用（原币种）；无法计价时返回 {@link Charge#ZERO}
     */
    public static Charge chargeOf(AiUsage usage, SysAiPricing pricing) {
        if (usage == null) {
            return Charge.ZERO;
        }
        if (usage.getTotalCost() != null && usage.getTotalCost().signum() > 0) {
            return new Charge(usage.getTotalCost(), usage.getCurrency());
        }
        if (pricing == null) {
            return Charge.ZERO;
        }
        BigDecimal inputPrice = decimal(pricing.getInputPricePer1k());
        BigDecimal outputPrice = decimal(pricing.getOutputPricePer1k());
        if (inputPrice == null && outputPrice == null) {
            return Charge.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        if (inputPrice != null) {
            int inputTokens = count(usage.getInputTokens());
            total = total.add(price(inputTokens, inputPrice));
            // 缓存命中按输入单价的 5 折单独记一笔：与 SysAiUsageServiceImpl 完全一致
            int cacheTokens = count(usage.getCacheTokens());
            if (cacheTokens > 0) {
                total = total.add(price(cacheTokens, inputPrice).multiply(CACHE_PRICE_FACTOR));
            }
        }
        if (outputPrice != null) {
            total = total.add(price(count(usage.getOutputTokens()), outputPrice));
        }
        return new Charge(total, pricing.getCurrency());
    }

    /**
     * 把费用折成人民币（Key 余额列的记账单位）。
     *
     * <p>币种为空或无法识别时按原值记账、不做猜测换算：乱乘一个汇率比不乘更危险。
     * 汇率缺失 / 非正数时同样按原值，调用方负责给默认汇率。</p>
     *
     * @param charge  费用，可为 null
     * @param rateUsd 1 美元兑换的人民币数量，可为 null
     * @param rateEur 1 欧元兑换的人民币数量，可为 null
     * @return 人民币金额，已按 {@link #AMOUNT_SCALE} 四舍五入
     */
    public static BigDecimal toCny(Charge charge, BigDecimal rateUsd, BigDecimal rateEur) {
        if (charge == null || !charge.positive()) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = charge.amount();
        BigDecimal rate = rateOf(charge.currency(), rateUsd, rateEur);
        if (rate != null && rate.signum() > 0) {
            amount = amount.multiply(rate);
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 按落库精度归一。
     *
     * @param amount 金额，可为 null
     * @return 保留 {@link #AMOUNT_SCALE} 位小数的金额；null 视为 0
     */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 按币种选汇率。
     *
     * @param currency 币种
     * @param rateUsd  美元汇率
     * @param rateEur  欧元汇率
     * @return 汇率；人民币 / 未知币种返回 null（表示不换算）
     */
    private static BigDecimal rateOf(String currency, BigDecimal rateUsd, BigDecimal rateEur) {
        if (currency == null || currency.isBlank()) {
            return null;
        }
        return switch (currency.trim().toUpperCase(Locale.ROOT)) {
            case "USD" -> rateUsd;
            case "EUR" -> rateEur;
            default -> null;
        };
    }

    /**
     * 令牌数 × 每 1k 单价 ÷ 1000。
     *
     * @param tokens 令牌数
     * @param price  每 1k 令牌单价
     * @return 金额
     */
    private static BigDecimal price(int tokens, BigDecimal price) {
        return BigDecimal.valueOf(tokens)
                .divide(TOKENS_PER_PRICE_UNIT, CALC_SCALE, RoundingMode.HALF_UP)
                .multiply(price);
    }

    /**
     * 安全的令牌数转换。
     *
     * @param value 令牌字段，可为 null
     * @return 小于等于 0 时返回 0
     */
    private static int count(Integer value) {
        return value == null || value <= 0 ? 0 : value;
    }

    /**
     * 安全的金额字段转换。
     *
     * @param value 金额字段，可为 null
     * @return null 或非正数返回 null
     */
    private static BigDecimal decimal(Double value) {
        return value == null || value <= 0 ? null : BigDecimal.valueOf(value);
    }

    /**
     * 判断金额字段是否为正。
     *
     * @param value 金额字段，可为 null
     * @return 大于零返回 true
     */
    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
