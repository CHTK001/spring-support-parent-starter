package com.chua.starter.test.controller;

import com.chua.starter.common.support.api.annotations.ApiGray;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * api 灰度演示控制器：灰度用户名单（username 维度）内放行，名单外 403。
 *
 * <p>用户信息从会话属性（userId/username/roles）解析，与生产鉴权上下文一致。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@RestController
@RequestMapping("/api/api-version/preview")
public class ApiGrayDemoController {

    @GetMapping
    @ApiGray(users = {"gray-user"})
    public Map<String, Object> preview() {
        return Map.of("result", "gray-preview-ok");
    }
}
