package com.chua.starter.account.impl;

import com.chua.captcha.support.CaptchaParser;
import com.chua.captcha.support.CaptchaSetting;
import com.chua.starter.account.CaptchaSolver;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * YesCaptcha Turnstile 求解器实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class YesCaptchaSolver implements CaptchaSolver {

    private static final String API_URL = "https://api.yescaptcha.com";
    private static final int MAX_RETRY = 30;
    private static final int RETRY_DELAY_SEC = 2;

    private final String apiKey;
    private final CaptchaParser parser;

    public YesCaptchaSolver(String apiKey) {
        this.apiKey = apiKey;
        this.parser = createParser();
    }

    @Override
    public String solve(String siteUrl, String siteKey, String proxyUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[YesCaptcha] API Key 未配置");
            return null;
        }
        try {
            Map<String, String> options = new HashMap<>();
            options.put("captchaType", "Turnstile");
            options.put("url", siteUrl);
            options.put("siteKey", siteKey);

            String taskId = parser.submitCaptcha(new byte[0], options);
            if (taskId == null) {
                log.warn("[YesCaptcha] 创建任务失败");
                return null;
            }
            log.info("[YesCaptcha] 任务已创建: {}", taskId);

            for (int i = 0; i < MAX_RETRY; i++) {
                Thread.sleep(RETRY_DELAY_SEC * 1000L);
                var result = parser.queryResult(taskId);
                if (result.isSuccess() && result.getToken() != null) {
                    log.info("[YesCaptcha] 求解成功");
                    return result.getToken();
                }
                if (!result.isSuccess() && !"processing".equals(result.getMessage())) {
                    log.warn("[YesCaptcha] 求解失败: {}", result.getMessage());
                    return null;
                }
            }
            log.warn("[YesCaptcha] 超时");
            return null;
        } catch (Exception e) {
            log.error("[YesCaptcha] 求解异常: {}", e.getMessage());
            return null;
        }
    }

    private CaptchaParser createParser() {
        CaptchaSetting setting = CaptchaSetting.builder()
                .apiToken(apiKey)
                .apiUrl(API_URL)
                .build();
        return new com.chua.captcha.support.YesCaptchaClient(setting);
    }
}
