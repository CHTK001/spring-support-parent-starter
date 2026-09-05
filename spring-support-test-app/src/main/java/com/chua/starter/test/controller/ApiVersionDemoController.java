package com.chua.starter.test.controller;

import com.chua.starter.common.support.api.annotations.ApiVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * api 版本路由演示控制器：同一路径按版本路由到不同实现。
 *
 * <p>版本来源：查询参数 {@code apiVersion=1|v1|latest} 或请求头 {@code X-Api-Version: 1}，
 * 由 {@code ApiVersionRequestMappingHandlerMapping} 在映射层完成路由。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@RestController
@RequestMapping("/api/api-version/greet")
public class ApiVersionDemoController {

    @GetMapping
    @ApiVersion("1")
    public Map<String, Object> greetV1() {
        return Map.of("version", "1", "message", "hello-v1");
    }

    @GetMapping
    @ApiVersion("2")
    public Map<String, Object> greetV2() {
        return Map.of("version", "2", "message", "hello-v2");
    }
}
