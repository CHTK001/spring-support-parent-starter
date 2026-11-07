package com.chua.starter.eduai.service;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.eduai.configuration.EduAiProperties;
import com.chua.starter.eduai.model.vo.MarkingSettingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 阅卷运行时设置服务。
 *
 * <p>数据来源为 AI 全局设置表 {@code sys_ai_global_setting} 的 {@code marking} 分组
 * （由 {@code MarkingGlobalSettingProvider} 定义、AI 配置中心「试卷配置」页维护），
 * 与 {@code paper} 分组并列；未配置的项回落到 application.yml 的 {@code plugin.eduai.*} 默认值。</p>
 *
 * <p>为什么走 {@link GlobalSettingReader#getStrDirect(String, String)}：设置页保存后写的是
 * <b>全局层</b>，而 {@code getStr} 会优先读当前用户的覆盖层并走 Redis 缓存 —— 阅卷是后台任务、
 * 常常没有登录用户上下文，用合并视图会「页面保存了、判分却按旧值跑」。直读全局层与
 * runtime / paper 两个系统分组同口径。</p>
 *
 * <p>设置会被判分热路径（每题一次模型调用）读取，直接查库开销过大，因此这里做了一层
 * 约 {@value #CACHE_TTL_MILLIS} 毫秒的快照缓存：既避免把 MySQL 打满，又保证页面保存后
 * 秒级生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarkingSettingService {

    /**
     * 全局设置分组标识
     */
    private static final String GROUP = "marking";

    /**
     * 快照缓存有效期（毫秒）
     */
    private static final long CACHE_TTL_MILLIS = 5000L;

    /**
     * 识别策略兜底值：整卷
     */
    private static final String STRATEGY_WHOLE = "whole";

    /**
     * 匹配模式：严谨（作答必须落在空内）
     */
    public static final String MATCH_MODE_STRICT = "strict";

    /**
     * 匹配模式：宽松（只看文本相似度，不做位置门禁）
     */
    public static final String MATCH_MODE_LOOSE = "loose";

    private final EduAiProperties properties;

    /**
     * 全局设置读取器（ai-starter 装配；缺失时全部回落到 YAML 默认值）
     */
    @Autowired(required = false)
    private GlobalSettingReader globalSettingReader;

    /**
     * 当前生效的设置快照（缓存）
     */
    private volatile MarkingSettingVO cached;

    /**
     * 快照写入时间
     */
    private volatile long cachedAt;

    /**
     * 读取当前生效的阅卷设置（带短时缓存）
     *
     * @return 合并后的设置；未保存过任何项时等于 application.yml 的默认值
     */
    public MarkingSettingVO resolved() {
        MarkingSettingVO snapshot = this.cached;
        if (snapshot != null && System.currentTimeMillis() - this.cachedAt < CACHE_TTL_MILLIS) {
            return snapshot;
        }
        MarkingSettingVO fresh = load();
        this.cached = fresh;
        this.cachedAt = System.currentTimeMillis();
        return fresh;
    }

    /**
     * 当前匹配模式
     *
     * @return {@link #MATCH_MODE_STRICT} 或 {@link #MATCH_MODE_LOOSE}
     */
    public String matchMode() {
        return resolved().getMatchMode();
    }

    /**
     * 是否严谨模式：学生作答必须落在空内，否则不得分
     *
     * @return true 表示严谨模式
     */
    public boolean strictMatch() {
        return MATCH_MODE_STRICT.equals(matchMode());
    }

    /**
     * 是否「判分完成即自动结案」。
     *
     * <p>打开后后台判分线程在把任务置为「已判分」之后，会立即按老师点「完成」的
     * 同一套口径登记错题并置「已完成」；关闭时维持原行为，停在「已判分」等老师确认。</p>
     *
     * @return true 表示判分完成即自动结案
     */
    public boolean autoFinish() {
        return resolved().isAutoFinish();
    }

    /**
     * 从全局设置表 + YAML 默认值合并出一份设置
     *
     * @return 设置
     */
    private MarkingSettingVO load() {
        EduAiProperties.Retry defaults = retryDefaults();
        MarkingSettingVO setting = new MarkingSettingVO();

        setting.setRetryTimes(readInt("retry_times", defaults.getTimes()));
        setting.setRetryBackoffMillis(readLong("retry_backoff_millis", defaults.getBackoffMillis()));
        setting.setRetryBackoffMultiplier(readDouble("retry_backoff_multiplier", defaults.getBackoffMultiplier()));
        setting.setRetryMaxBackoffMillis(readLong("retry_max_backoff_millis", defaults.getMaxBackoffMillis()));
        setting.setRetryOnTransient(readBool("retry_on_transient", defaults.isRetryOnTransient()));
        setting.setExtractStrategy(normalizeStrategy(read("extract_strategy", defaultStrategy())));
        setting.setMatchMode(normalizeMatchMode(read("match_mode", MATCH_MODE_STRICT)));
        setting.setMarkingPrompt(read("marking_system_prompt", ""));
        setting.setAutoFinish(readBool("auto_finish", false));
        // 兜底纠偏：页面上可能填了非法值（负数 / 0 / 倍数 <=1），判分前统一按默认规则纠正
        if (setting.getRetryTimes() < 0) {
            setting.setRetryTimes(defaults.getTimes());
        }
        if (setting.getRetryBackoffMillis() <= 0) {
            setting.setRetryBackoffMillis(defaults.getBackoffMillis());
        }
        if (setting.getRetryBackoffMultiplier() <= 1) {
            setting.setRetryBackoffMultiplier(defaults.getBackoffMultiplier());
        }
        if (setting.getRetryMaxBackoffMillis() <= 0) {
            setting.setRetryMaxBackoffMillis(defaults.getMaxBackoffMillis());
        }
        return setting;
    }

    /**
     * 读取一个字段的字符串值；全局设置读取器缺失时返回兜底值
     *
     * @param field        字段名
     * @param defaultValue 兜底值
     * @return 字段值
     */
    private String read(String field, String defaultValue) {
        if (globalSettingReader == null) {
            return defaultValue;
        }
        try {
            String value = globalSettingReader.getStrDirect(GROUP, field);
            return StringUtils.hasText(value) ? value.trim() : defaultValue;
        } catch (Exception e) {
            log.debug("[MarkingSetting] 读取全局设置失败(field={}): {}", field, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * 读取整型字段
     *
     * @param field        字段名
     * @param defaultValue 兜底值
     * @return 字段值
     */
    private int readInt(String field, int defaultValue) {
        String raw = read(field, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 读取长整型字段
     *
     * @param field        字段名
     * @param defaultValue 兜底值
     * @return 字段值
     */
    private long readLong(String field, long defaultValue) {
        String raw = read(field, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 读取浮点字段
     *
     * @param field        字段名
     * @param defaultValue 兜底值
     * @return 字段值
     */
    private double readDouble(String field, double defaultValue) {
        String raw = read(field, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 读取布尔字段
     *
     * @param field        字段名
     * @param defaultValue 兜底值
     * @return 字段值
     */
    private boolean readBool(String field, boolean defaultValue) {
        String raw = read(field, null);
        if (raw == null) {
            return defaultValue;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower);
    }

    /**
     * 取 YAML 侧的重试默认值；缺失时回退 {@link EduAiProperties.Retry} 的字段默认
     *
     * @return 重试默认配置
     */
    private EduAiProperties.Retry retryDefaults() {
        if (properties.getAi() == null || properties.getAi().getRetry() == null) {
            return new EduAiProperties.Retry();
        }
        return properties.getAi().getRetry();
    }

    /**
     * 取 YAML 侧的识别策略默认值（规范化小写）
     *
     * @return 策略 key
     */
    private String defaultStrategy() {
        String strategy = properties.getMarking() == null ? null : properties.getMarking().getExtractStrategy();
        return strategy == null || strategy.isBlank() ? STRATEGY_WHOLE : normalizeStrategy(strategy);
    }

    /**
     * 规范化识别策略：去空白 + 小写；空值时回退整卷
     *
     * @param strategy 原始策略
     * @return 规范化后的策略 key
     */
    private static String normalizeStrategy(String strategy) {
        return strategy == null || strategy.isBlank()
                ? STRATEGY_WHOLE
                : strategy.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 规范化匹配模式：只认 strict / loose，其余回退严谨
     *
     * @param mode 原始模式
     * @return 规范化后的模式 key
     */
    private static String normalizeMatchMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MATCH_MODE_STRICT;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return MATCH_MODE_LOOSE.equals(normalized) ? MATCH_MODE_LOOSE : MATCH_MODE_STRICT;
    }
}
