package com.chua.starter.datasearch.pojo.exchangerate;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 汇率概览出参（顶栏汇率的唯一契约）。
 *
 * <p>字段命名与前端 {@code @repo/core} 的 {@code ExchangeRateOverview} 一一对应：
 * 最新价 + 较周期起点的涨跌（额/率）+ 周期内最高/最低。前端
 * {@code normalizeBackendOverview} 只强依赖 {@code rates[].symbol} 与 {@code rates[].rate}，
 * 其余字段缺省会被补齐为 NaN（展示为 "--"），因此这里一律给基本类型数值，不出 null。</p>
 *
 * @param source  数据来源（open-exchange-rate / frankfurter）
 * @param period  查询周期（1m/3m/6m/12m）
 * @param symbols 关注的币种对（base/quote）
 * @param rates   各币种对的汇率点
 * @author CH
 * @since 4.0.0.53
 */
@Schema(description = "汇率概览")
public record ExchangeRateOverviewVO(
        @Schema(description = "数据来源") String source,
        @Schema(description = "查询周期（1m/3m/6m/12m）") String period,
        @Schema(description = "关注的币种对（base/quote）") List<String> symbols,
        @Schema(description = "各币种对的汇率点") List<Rate> rates) {

    /**
     * 单条汇率点。
     *
     * @param symbol        币种对（base/quote）
     * @param rate          最新价（1 unit base = ? quote）
     * @param change        较周期起点的涨跌额
     * @param changePercent 较周期起点的涨跌幅（%）
     * @param high          周期内最高价
     * @param low           周期内最低价
     */
    @Schema(description = "单条汇率点")
    public record Rate(
            @Schema(description = "币种对（base/quote）") String symbol,
            @Schema(description = "最新价（1 unit base = ? quote）") double rate,
            @Schema(description = "较周期起点的涨跌额") double change,
            @Schema(description = "较周期起点的涨跌幅（%）") double changePercent,
            @Schema(description = "周期内最高价") double high,
            @Schema(description = "周期内最低价") double low) {
    }
}
