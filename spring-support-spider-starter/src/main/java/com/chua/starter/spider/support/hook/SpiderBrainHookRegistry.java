package com.chua.starter.spider.support.hook;

import com.chua.spider.support.brain.SpiderBrainHook;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 平台层自定义钩子注册表。
 *
 * <p>维护所有已注册的 {@link SpiderBrainHook} 实现，并在启动时自动注册
 * {@link SpiderLoginHook} 和 {@link SpiderCaptchaHook}。</p>
 *
 * <p>外部可通过 {@link #register(SpiderBrainHook)} 追加自定义钩子，
 * 通过 {@link #getHooks()} 获取全部钩子，
 * 通过 {@link #getHooksByType(String)} 按钩子类名过滤。</p>
 *
 * @author CH
 */
@Slf4j
@Component
public class SpiderBrainHookRegistry {

    private final List<SpiderBrainHook> hooks = new ArrayList<>();

    private final SpiderLoginHook loginHook;
    private final SpiderCaptchaHook captchaHook;

    public SpiderBrainHookRegistry(SpiderLoginHook loginHook, SpiderCaptchaHook captchaHook) {
        this.loginHook = loginHook;
        this.captchaHook = captchaHook;
    }

    /**
     * 启动时自动注册内置钩子。
     */
    @PostConstruct
    public void init() {
        register(loginHook);
        register(captchaHook);
        log.info("[Spider] SpiderBrainHookRegistry 初始化完成，已注册钩子数={}", hooks.size());
    }

    /**
     * 注册一个钩子实现。
     *
     * @param hook 钩子实例，不能为 null
     */
    public void register(SpiderBrainHook hook) {
        if (hook == null) {
            return;
        }
        hooks.add(hook);
        log.debug("[Spider] 注册钩子: {}", hook.getClass().getSimpleName());
    }

    /**
     * 获取所有已注册的钩子（不可修改视图）。
     *
     * @return 钩子列表
     */
    public List<SpiderBrainHook> getHooks() {
        return Collections.unmodifiableList(hooks);
    }

    /**
     * 按钩子类型（简单类名）过滤钩子。
     *
     * @param hookType 钩子类的简单类名，如 {@code "SpiderLoginHook"}
     * @return 匹配的钩子列表
     */
    public List<SpiderBrainHook> getHooksByType(String hookType) {
        if (hookType == null || hookType.isBlank()) {
            return Collections.emptyList();
        }
        return hooks.stream()
                .filter(h -> h.getClass().getSimpleName().equals(hookType))
                .collect(Collectors.toList());
    }
}
