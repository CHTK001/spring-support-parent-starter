package com.chua.starter.datasearch.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasearch.pojo.exchangerate.ExchangeRateOverviewVO;
import com.chua.starter.datasearch.service.ExchangeRateDataService;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 顶栏汇率接口。
 *
 * <p>前端启动期用 HEAD 探测本接口判断后台是否提供汇率能力，命中即以本接口为准，
 * 未命中才回落 frankfurter。因此：</p>
 * <ul>
 *   <li>参数全部可选（HEAD 不带查询串也要能命中映射）；</li>
 *   <li>匿名可访问（顶栏在登录前就要出数）；</li>
 *   <li>无可用数据源时返回失败码，前端据此回落三方，而不是返回半截数据。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Tag(name = "顶栏汇率接口")
@RestController
@RequestMapping("/v2/exchange-rate")
@RequiredArgsConstructor
public class ExchangeRateV2Controller {

    /**
     * 币种对分隔符（前端按逗号拼接多个币种对，也接受重复 query 参数）
     */
    private static final String SYMBOL_DELIMITER = ",";

    /**
     * 汇率数据服务
     */
    private final ExchangeRateDataService exchangeRateDataService;

    /**
     * 汇率概览（最新价 + 周期涨跌 + 周期极值）
     *
     * @param symbols 币种对，逗号分隔或重复传参（如 {@code USD/EUR,USD/CNY}），缺省取内置篮子
     * @param period  周期（1m/3m/6m/12m），缺省 1m
     * @return 汇率概览
     */
    @Operation(summary = "汇率概览")
    @Parameter(name = "symbols", description = "币种对，逗号分隔，如 USD/EUR,USD/CNY")
    @Parameter(name = "period", description = "周期：1m/3m/6m/12m，默认 1m")
    @TokenForIgnore
    @GetMapping("/overview")
    public ReturnResult<ExchangeRateOverviewVO> overview(
            @RequestParam(value = "symbols", required = false) List<String> symbols,
            @RequestParam(value = "period", required = false) String period) {
        try {
            ExchangeRateOverviewVO overview =
                    exchangeRateDataService.overview(flatten(symbols), period);
            return overview == null
                    ? ReturnResult.error("无可用汇率数据源")
                    : ReturnResult.ok(overview);
        } catch (Exception e) {
            log.warn("[exchange-rate] 汇率概览失败: {}", e.getMessage());
            return ReturnResult.error("汇率查询失败：" + e.getMessage());
        }
    }

    /**
     * 展开币种对入参。
     *
     * <p>前端 axios 对数组参数默认序列化为 {@code symbols[]=USD/EUR}（重复键），
     * 也有调用方直接拼 {@code symbols=USD/EUR,USD/CNY}。两种都收，否则前端
     * 传数组时后台会把 "USD/EUR,USD/CNY" 当成一个非法币种对整条丢掉。</p>
     *
     * @param symbols 原始入参
     * @return 展开后的币种对
     */
    private List<String> flatten(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return null;
        }
        return symbols.stream()
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> Arrays.stream(value.split(SYMBOL_DELIMITER)))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
