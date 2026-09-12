package com.chua.starter.test.controller;

import com.chua.starter.common.support.api.annotations.ApiFeature;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * api 功能开关演示控制器：{@code @ApiFeature} 标记功能的开放状态控制。
 *
 * <p>未开放的功能（{@code defaultEnabled=false}）经 ApiControlInterceptor 拦截返回
 * {@code disabledStatus}（默认 503）+ {@code disabledMessage}；开放功能正常放行。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@RestController
@RequestMapping("/api/feature")
public class ApiFeatureDemoController {

    /**
     * 实验性功能：默认关闭 -> 拦截返回 503
     *
     * @return beta的结果
     */
    @GetMapping("/beta")
    @ApiFeature(value = "beta-feature", defaultEnabled = false)
    public Map<String, Object> beta() {
        return Map.of("result", "beta-ok");
    }

    /**
     * 常规功能：默认开放 -> 正常放行
     *
     * @return 打开的结果
     */
    @GetMapping("/open")
    @ApiFeature(value = "open-feature", defaultEnabled = true)
    public Map<String, Object> open() {
        return Map.of("result", "open-ok");
    }
}
