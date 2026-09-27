package com.chua.starter.datasearch.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasearch.pojo.weather.WeatherOverviewVO;
import com.chua.starter.datasearch.query.WeatherQuery;
import com.chua.starter.datasearch.service.WeatherDataService;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顶栏天气接口。
 *
 * <p>前端启动期用 HEAD 探测本接口判断后台是否提供天气能力，命中即以本接口为准，
 * 未命中才回落三方。因此：</p>
 * <ul>
 *   <li>参数全部可选（HEAD 不带查询串也要能命中映射）；</li>
 *   <li>匿名可访问（顶栏在登录前就要出数）；</li>
 *   <li>无可用数据源时返回失败码，前端据此回落三方，而不是返回半截数据。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Tag(name = "顶栏天气接口")
@RestController
@RequestMapping("/v2/weather")
@RequiredArgsConstructor
public class WeatherV2Controller {

    /**
     * 天气数据服务
    */
    private final WeatherDataService weatherDataService;

    /**
     * 天气总览（实况 + 逐小时 + 逐日）
     *
     * <p>入参按 Spring MVC 的隐式 {@code @ModelAttribute} 绑定（非简单类型默认走模型绑定），
     * 故不依赖 springdoc 的 {@code @ParameterObject}——那枚注解只影响文档里的参数展开，
     * 绑定行为一致，避免本模块为一个文档注解引入 springdoc 依赖。</p>
     *
     * @param query 查询条件（坐标优先，缺省按城市）
     * @return 天气总览
     */
    @Operation(summary = "天气总览")
    @TokenForIgnore
    @GetMapping("/overview")
    public ReturnResult<WeatherOverviewVO> overview(WeatherQuery query) {
        try {
            WeatherOverviewVO overview = weatherDataService.overview(query);
            return overview == null
                    ? ReturnResult.error("无可用天气数据源")
                    : ReturnResult.ok(overview);
        } catch (Exception e) {
            log.warn("[weather] 天气总览失败: {}", e.getMessage());
            return ReturnResult.error("天气查询失败：" + e.getMessage());
        }
    }
}
