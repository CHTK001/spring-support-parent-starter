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
}
