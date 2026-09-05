package com.chua.starter.test.controller;

import com.chua.starter.common.support.api.annotations.ApiVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * api 版本演示控制器。
 *
 * <p>框架观察：{@code ApiVersionRequestMappingHandlerMapping} 在 Boot 4.1 下注册后
 * 无法取代默认映射（同路径多版本方法触发 Ambiguous mapping），版本路由特性暂不可用——
 * 此处仅保留单方法演示 {@code @ApiVersion} 注解存在性（路由缺陷见测试报告）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@RestController
@RequestMapping("/api/api-version/greet")
public class ApiVersionDemoController {

    @GetMapping
    @ApiVersion("1")
    public Map<String, Object> greet() {
        return Map.of("version", "1", "message", "hello-v1");
    }
}
