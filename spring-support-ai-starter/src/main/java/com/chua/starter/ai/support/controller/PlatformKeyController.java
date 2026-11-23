// [P3C A20 豁免] A20/A30/A31: 含日志/界面文案等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.service.PlatformAccessKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台访问密钥校验接口。
 *
 * <p>Web 浏览器推理的「使用」发生在浏览器里（模型脚本动态加载、页面内推理），
 * 后端无法在对话链路上拦截，因此前端开聊前必须先调本接口由<b>后端</b>校验密钥
 * （安全性校验不放在前端）。ONNX 平台的模型目录与对话链路在后端逐请求校验，
 * 本接口同时作为两个平台统一的密钥自检入口。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestController
@RequestMapping("/v2/platform-keys")
@RequiredArgsConstructor
@Slf4j
public class PlatformKeyController {

    /**
     * 平台访问密钥校验服务
     */
    private final PlatformAccessKeyService platformAccessKeyService;

    /**
     * 校验平台访问密钥。
     *
     * <p>请求体：{@code {"platform": "onnx"|"web", "key": "..."}}。
     * 响应：{@code {"platform": "...", "valid": true|false}}。
     * 管理员恒为 valid；平台未配置密钥时视为未启用校验，恒为 valid。</p>
     *
     * @param body 请求体，不允许为 null
     * @return 校验结果
     */
    @PostMapping("/verify")
    public ApiResult<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        Object platformObj = body == null ? null : body.get("platform");
        Object keyObj = body == null ? null : body.get("key");
        String platform = platformObj == null ? "" : String.valueOf(platformObj);
        String key = keyObj == null ? "" : String.valueOf(keyObj);

        String normalized = platformAccessKeyService.normalizePlatform(platform);
        if (normalized == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "未知平台: " + platform);
        }
        boolean valid = platformAccessKeyService.verify(normalized, key);
        if (!valid) {
            log.info("[PlatformKey] 密钥校验未通过: platform={}", normalized);
        }
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("platform", normalized);
        data.put("valid", valid);
        return ApiResult.ok(data);
    }
}
