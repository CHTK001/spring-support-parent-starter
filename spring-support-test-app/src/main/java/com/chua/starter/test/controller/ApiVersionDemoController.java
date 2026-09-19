package com.chua.starter.test.controller;

import com.chua.starter.common.support.api.annotations.ApiVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * api 版本演示控制器：同路径多版本路由。
 *
 * <p>{@code @ApiVersion} 标注在方法上，由 {@code ApiVersionRequestMappingHandlerMapping}
 * （经 {@code WebMvcRegistrations} 装配）按查询参数 {@code apiVersion} / 请求头
 * {@code X-Api-Version} 路由；{@code latest} 关键字匹配最新版本。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@RestController
@RequestMapping("/api/api-version/greet")
public class ApiVersionDemoController {

    @GetMapping
    @ApiVersion("1")
    /**
     * greetv1。
     * @return greetV1的结果
     */
    public Map<String, Object> greetV1() {
        return Map.of("version", "1", "message", "hello-v1");
    }

    @GetMapping
    @ApiVersion("2")
    /**
     * greetv2。
     * @return greetV2的结果
     */
    public Map<String, Object> greetV2() {
        return Map.of("version", "2", "message", "hello-v2");
    }
}
