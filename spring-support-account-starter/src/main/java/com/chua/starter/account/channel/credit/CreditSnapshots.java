// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.credit;

import com.chua.starter.datasearch.credit.model.CreditResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道余额快照 → 余额引擎结果的共享装配器。
 *
 * <p>各家渠道实现内部仍产出「旧契约」的快照 Map（{@code remain} / {@code total} /
 * {@code unit} / {@code currency} / {@code packages} / {@code error} 等，字段全部可空），
 * 本类把它装入 {@link CreditResult}：{@code amount = remain}、
 * {@code currency = currency ?: unit}、{@code extra = 完整快照}、{@code error} 透传。</p>
 *
 * <p>快照原样进 {@code extra}：前端 {@code /credit} 契约依赖这些字段，
 * 引擎侧只做包装，不丢任何实现自带的读数。</p>
 *
 * @since 4.0.0.58
 * @author CH
 */
public final class CreditSnapshots {

    private CreditSnapshots() {
    }

    /**
     * 把渠道实现产出的快照 Map 装入引擎结果。
     *
     * <p>实现约定「永不抛异常」：失败信息写在快照的 {@code error} 键里，这里透传；
     * 快照为 {@code null} 视为查询失败。</p>
     *
     * @param channel  渠道标识（写入 {@code source}）
     * @param snapshot 渠道实现产出的快照（可为 null）
     * @return 引擎结果；{@code extra} 永不为 null
     */
    public static CreditResult fromSnapshot(String channel, Map<String, Object> snapshot) {
        CreditResult result = new CreditResult();
        result.setSource(channel);
        Map<String, Object> extra = snapshot == null ? new LinkedHashMap<>() : snapshot;
        result.setExtra(extra);
        if (snapshot == null) {
            result.setError("余额快照为空");
            return result;
        }
        Object error = extra.get("error");
        if (error != null && !String.valueOf(error).isBlank()) {
            result.setError(String.valueOf(error));
        }
        Object remain = extra.get("remain");
        if (remain instanceof Number number) {
            result.setAmount(BigDecimal.valueOf(number.doubleValue()));
        } else if (remain instanceof String text && !text.isBlank()) {
            try {
                result.setAmount(new BigDecimal(text.trim()));
            } catch (NumberFormatException ignored) {
                // remain 不是数值（如「未知」）：保持 amount 为空，前端按原文展示
            }
        }
        Object currency = extra.get("currency");
        if (currency == null || String.valueOf(currency).isBlank()) {
            currency = extra.get("unit");
        }
        if (currency != null && !String.valueOf(currency).isBlank()) {
            result.setCurrency(String.valueOf(currency));
        }
        return result;
    }
}
