package com.chua.starter.datasearch.service;

import com.chua.starter.datasearch.pojo.exchangerate.ExchangeRateOverviewVO;

import java.util.List;

/**
 * 顶栏汇率数据服务。
 *
 * <p>把「最新价 + 周期涨跌 + 周期极值」归一成本系统契约：最新价优先取
 * {@code ExchangeRateProvider} SPI（open.er-api.com，USD 基准，6 小时缓存），
 * 周期序列取 frankfurter 的日期区间接口（一次拿整段，避免逐日外呼）。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface ExchangeRateDataService {

    /**
     * 汇率概览。
     *
     * @param symbols 币种对（支持 {@code USD/EUR} 与 {@code USDEUR} 两种写法），为空取内置篮子
     * @param period  周期（1m/3m/6m/12m），为空或非法取 1m
     * @return 汇率概览；无任何可用数据时返回 空，由调用方给出失败码
     */
    ExchangeRateOverviewVO overview(List<String> symbols, String period);
}
