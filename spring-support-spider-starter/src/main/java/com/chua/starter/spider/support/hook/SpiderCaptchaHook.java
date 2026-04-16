package com.chua.starter.spider.support.hook;

import com.chua.common.support.ai.brain.Brain;
import com.chua.spider.Page;
import com.chua.spider.Request;
import com.chua.spider.Task;
import com.chua.spider.support.brain.SpiderBrainHook;
import com.chua.spider.support.model.SpiderTaskDefinition;
import com.chua.starter.spider.support.engine.HumanInputSuspendRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 验证码钩子（afterDownload）。
 *
 * <p>在页面下载完成后检测页面内容是否包含验证码特征（常见关键词或元素），
 * 若检测到验证码则触发 HUMAN_INPUT 节点挂起，等待人工介入处理。</p>
 *
 * <p>若页面不含验证码特征，则直接透传，不做任何处理。</p>
 *
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpiderCaptchaHook implements SpiderBrainHook {

    /** Request extra 键：任务 ID（用于触发 HUMAN_INPUT 挂起） */
    public static final String EXTRA_TASK_ID = "spider.platformTaskId";

    /** Request extra 键：验证码节点 ID */
    public static final String EXTRA_CAPTCHA_NODE_ID = "spider.captchaNodeId";

    /** 默认验证码节点 ID（当未指定时使用） */
    private static final String DEFAULT_CAPTCHA_NODE_ID = "captcha-node";

    /** 常见验证码关键词（不区分大小写） */
    private static final List<String> CAPTCHA_KEYWORDS = List.of(
            "captcha",
            "验证码",
            "图形验证",
            "滑动验证",
            "人机验证",
            "security check",
            "are you human",
            "robot check",
            "recaptcha",
            "hcaptcha",
            "geetest",
            "极验",
            "请完成安全验证",
            "请输入验证码"
    );

    /** 常见验证码 HTML 元素特征 */
    private static final List<String> CAPTCHA_HTML_PATTERNS = List.of(
            "id=\"captcha\"",
            "class=\"captcha\"",
            "name=\"captcha\"",
            "id=\"recaptcha\"",
            "class=\"g-recaptcha\"",
            "data-sitekey=",
            "class=\"geetest_",
            "id=\"nc_1_",
            "class=\"nc-container\""
    );

    private final HumanInputSuspendRegistry humanInputSuspendRegistry;

    @Override
    public void afterDownload(Brain brain, SpiderTaskDefinition definition,
                              Request request, Page page, Task task) {
        if (request == null || page == null) {
            return;
        }

        String rawText = page.getRawText();
        if (StringUtils.isBlank(rawText)) {
            return;
        }

        if (!containsCaptcha(rawText)) {
            return;
        }

        String url = request.getUrl();
        log.warn("[Spider][CaptchaHook] 检测到验证码页面 url={}", url);

        // 从 request extra 中读取平台任务 ID
        Object taskIdObj = request.getExtra(EXTRA_TASK_ID);
        if (taskIdObj == null) {
            log.warn("[Spider][CaptchaHook] 未找到平台任务 ID（extra key={}），无法触发 HUMAN_INPUT 挂起",
                    EXTRA_TASK_ID);
            return;
        }

        Long platformTaskId;
        try {
            platformTaskId = Long.parseLong(taskIdObj.toString());
        } catch (NumberFormatException e) {
            log.warn("[Spider][CaptchaHook] 平台任务 ID 格式非法: {}", taskIdObj);
            return;
        }

        // 读取验证码节点 ID（可由调用方通过 extra 指定，否则使用默认值）
        Object nodeIdObj = request.getExtra(EXTRA_CAPTCHA_NODE_ID);
        String nodeId = (nodeIdObj != null && !nodeIdObj.toString().isBlank())
                ? nodeIdObj.toString()
                : DEFAULT_CAPTCHA_NODE_ID;

        // 触发 HUMAN_INPUT 节点挂起
        log.info("[Spider][CaptchaHook] 触发 HUMAN_INPUT 挂起 taskId={} nodeId={}", platformTaskId, nodeId);
        humanInputSuspendRegistry.register(platformTaskId, nodeId);
    }

    /**
     * 检测页面内容是否包含验证码特征。
     *
     * @param rawText 页面原始 HTML 文本
     * @return {@code true} 表示检测到验证码
     */
    boolean containsCaptcha(String rawText) {
        if (StringUtils.isBlank(rawText)) {
            return false;
        }
        String lowerText = rawText.toLowerCase();

        for (String keyword : CAPTCHA_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) {
                log.debug("[Spider][CaptchaHook] 命中验证码关键词: {}", keyword);
                return true;
            }
        }

        for (String pattern : CAPTCHA_HTML_PATTERNS) {
            if (lowerText.contains(pattern.toLowerCase())) {
                log.debug("[Spider][CaptchaHook] 命中验证码 HTML 特征: {}", pattern);
                return true;
            }
        }

        return false;
    }
}
