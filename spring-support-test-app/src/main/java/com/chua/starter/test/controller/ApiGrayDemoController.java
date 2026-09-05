package com.chua.starter.test.controller;

import com.chua.starter.common.support.api.annotations.ApiGray;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * api 灰度演示控制器。
 *
 * <p>灰度语义：默认<b>软灰度</b>——命中用户（username 维度）获得灰度体验并返回
 * {@code X-Gray-Hit} 响应头，未命中用户照常走默认逻辑；{@code forceGray=true} 时为
 * <b>强制灰度</b>，未命中用户被拒绝（{@code notInGrayStatus} 指定状态码）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@RestController
@RequestMapping("/api/api-version")
public class ApiGrayDemoController {

    /** 软灰度：命中灰度用户名单返回灰度体验，未命中正常放行 */
    @GetMapping("/preview")
    @ApiGray(users = {"gray-user"})
    public Map<String, Object> preview() {
        return Map.of("result", "gray-preview-ok");
    }

    /** 强制灰度：未命中灰度用户名单直接拒绝（403） */
    @GetMapping("/preview-force")
    @ApiGray(users = {"gray-user"}, forceGray = true, notInGrayStatus = 403)
    public Map<String, Object> previewForce() {
        return Map.of("result", "gray-force-ok");
    }

    /** 灰度角色名单：用户角色含 gray-role 命中 */
    @GetMapping("/preview-roles")
    @ApiGray(roles = {"gray-role"})
    public Map<String, Object> previewByRole() {
        return Map.of("result", "gray-role-ok");
    }

    /** 灰度 IP 名单：客户端 IP 为 127.0.0.1 命中（IpUtils 优先 X-Forwarded-For） */
    @GetMapping("/preview-ips")
    @ApiGray(ips = {"127.0.0.1"})
    public Map<String, Object> previewByIp() {
        return Map.of("result", "gray-ip-ok");
    }

    /** 灰度请求头：X-Gray-Header=1 精确匹配命中 */
    @GetMapping("/preview-headers")
    @ApiGray(headers = {"X-Gray-Header=1"})
    public Map<String, Object> previewByHeader() {
        return Map.of("result", "gray-header-ok");
    }

    /** 灰度 SpEL 规则：username == 'spel-user' 命中 */
    @GetMapping("/preview-rule")
    @ApiGray(rule = "#username == 'spel-user'")
    public Map<String, Object> previewByRule() {
        return Map.of("result", "gray-rule-ok");
    }

    /** 灰度百分比 100：恒命中 */
    @GetMapping("/preview-percentage")
    @ApiGray(percentage = 100)
    public Map<String, Object> previewPercentageAll() {
        return Map.of("result", "gray-percent-ok");
    }

    /** 灰度百分比 0：恒不命中（percentage<=0 跳过百分比检查） */
    @GetMapping("/preview-percentage-zero")
    @ApiGray(percentage = 0)
    public Map<String, Object> previewPercentageNone() {
        return Map.of("result", "gray-percent-none");
    }
}
