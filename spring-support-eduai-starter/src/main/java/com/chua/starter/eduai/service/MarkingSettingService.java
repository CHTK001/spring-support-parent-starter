package com.chua.starter.eduai.service;

import com.chua.starter.eduai.configuration.EduAiProperties;
import com.chua.starter.eduai.model.vo.MarkingSettingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 阅卷运行时设置服务。
 *
 * <p>取值优先级：{@code edu_app_config} 键值表（系统配置弹窗写入） &gt; application.yml
 * 的 {@code plugin.eduai.*} 默认值。把两者合成一处，避免「重试配置」与「识别策略」
 * 各自散落在调用点，日后新增项只改这里。</p>
 *
 * <p>设置会被判分热路径（每题一次模型调用）读取，直接查库开销过大，因此这里做了一层
 * 约 {@value #CACHE_TTL_MILLIS} 毫秒的快照缓存：既避免把 MySQL 打满，又保证弹窗保存后
 * 秒级生效。保存时主动失效缓存，改完立即起作用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class MarkingSettingService {

    /**
     * 快照缓存有效期（毫秒）
     */
    private static final long CACHE_TTL_MILLIS = 5000L;

    /**
     * 识别策略兜底值：整卷
     */
    private static final String STRATEGY_WHOLE = "whole";

    private final EduAiProperties properties;

    private final EduAppConfigService appConfigService;

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
     * @return 合并后的设置；弹窗未保存过任何项时等于 application.yml 的默认值
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
     * 写入阅卷设置：逐项落到键值表，并失效缓存使其立即生效。
     *
     * @param setting 设置；非法值按兜底规则纠正后入库
     */
    public void save(MarkingSettingVO setting) {
        if (setting == null) {
            return;
        }
        appConfigService.set(EduAppConfigService.KEY_MARKING_RETRY_TIMES,
                String.valueOf(Math.max(0, setting.getRetryTimes())));
        appConfigService.set(EduAppConfigService.KEY_MARKING_RETRY_BACKOFF_MILLIS,
                String.valueOf(setting.getRetryBackoffMillis() > 0 ? setting.getRetryBackoffMillis() : 1000L));
        appConfigService.set(EduAppConfigService.KEY_MARKING_RETRY_BACKOFF_MULTIPLIER,
                String.valueOf(setting.getRetryBackoffMultiplier() > 1 ? setting.getRetryBackoffMultiplier() : 2.0D));
        appConfigService.set(EduAppConfigService.KEY_MARKING_RETRY_MAX_BACKOFF_MILLIS,
                String.valueOf(setting.getRetryMaxBackoffMillis() > 0 ? setting.getRetryMaxBackoffMillis() : 15000L));
        appConfigService.set(EduAppConfigService.KEY_MARKING_RETRY_ON_TRANSIENT,
                setting.isRetryOnTransient() ? "1" : "0");
        appConfigService.set(EduAppConfigService.KEY_MARKING_EXTRACT_STRATEGY, normalizeStrategy(setting.getExtractStrategy()));
        appConfigService.set(EduAppConfigService.KEY_MARKING_PROMPT, setting.getMarkingPrompt());
        this.cached = null;
    }

    /**
     * 从键值表 + 默认值合并出一份设置
     *
     * @return 设置
     */
    private MarkingSettingVO load() {
        EduAiProperties.Retry defaults = retryDefaults();
        MarkingSettingVO setting = new MarkingSettingVO();

        Integer times = appConfigService.getInteger(EduAppConfigService.KEY_MARKING_RETRY_TIMES);
        setting.setRetryTimes(times != null ? Math.max(0, times) : defaults.getTimes());

        Long backoff = appConfigService.getLong(EduAppConfigService.KEY_MARKING_RETRY_BACKOFF_MILLIS);
        setting.setRetryBackoffMillis(backoff != null && backoff > 0 ? backoff : defaults.getBackoffMillis());

        Double multiplier = appConfigService.getDouble(EduAppConfigService.KEY_MARKING_RETRY_BACKOFF_MULTIPLIER);
        setting.setRetryBackoffMultiplier(
                multiplier != null && multiplier > 1 ? multiplier : defaults.getBackoffMultiplier());

        Long maxBackoff = appConfigService.getLong(EduAppConfigService.KEY_MARKING_RETRY_MAX_BACKOFF_MILLIS);
        setting.setRetryMaxBackoffMillis(
                maxBackoff != null && maxBackoff > 0 ? maxBackoff : defaults.getMaxBackoffMillis());

        Boolean retryOnTransient = appConfigService.getBoolean(EduAppConfigService.KEY_MARKING_RETRY_ON_TRANSIENT);
        setting.setRetryOnTransient(retryOnTransient != null ? retryOnTransient : defaults.isRetryOnTransient());

        String strategy = appConfigService.getText(EduAppConfigService.KEY_MARKING_EXTRACT_STRATEGY);
        setting.setExtractStrategy(strategy != null ? normalizeStrategy(strategy) : defaultStrategy());
        setting.setMarkingPrompt(appConfigService.getText(EduAppConfigService.KEY_MARKING_PROMPT));
        return setting;
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
}
