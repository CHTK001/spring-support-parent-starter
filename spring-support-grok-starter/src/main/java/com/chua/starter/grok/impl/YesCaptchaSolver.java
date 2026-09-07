package com.chua.starter.grok.impl;

import com.chua.captcha.support.CaptchaParser;
import com.chua.captcha.support.CaptchaSetting;
import com.chua.starter.grok.CaptchaSolver;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * YesCaptcha Turnstile 求解器实现。
 *
 * <p>使用 YesCaptcha API 求解 Cloudflare Turnstile 验证码。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class YesCaptchaSolver implements CaptchaSolver {

    private static final String API_URL = "https://api.yescaptcha.com";
    private static final int SOFT_ID = 102154;
    private static final int MAX_RETRY = 30;
    private static final int INITIAL_DELAY = 5;
    private static final int RETRY_DELAY = 2;

    private final String apiKey;
    private final CaptchaParser parser;
    private final ScheduledExecutorService scheduler;

    public YesCaptchaSolver(String apiKey) {
        this.apiKey = apiKey;
        this.parser = createParser();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public String solve(String siteUrl, String siteKey, String proxyUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[YesCaptcha] API Key 未配置");
            return null;
        }
        try {
            // 创建任务
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

            // 轮询结果
            for (int i = 0; i < MAX_RETRY; i++) {
                Thread.sleep(RETRY_DELAY * 1000L);
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
