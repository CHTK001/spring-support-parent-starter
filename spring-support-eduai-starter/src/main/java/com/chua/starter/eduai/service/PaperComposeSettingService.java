package com.chua.starter.eduai.service;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.eduai.configuration.EduAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AI 组卷运行时设置服务。
 *
 * <p>数据来源为 AI 全局设置表 {@code sys_ai_global_setting} 的 {@code compose} 分组
 * （由 {@code PaperComposeGlobalSettingProvider} 定义、AI 配置中心「试卷配置」页维护），
 * 与 {@code paper}（试卷识别）、{@code marking}（阅卷设置）并列；未配置的项回落到
 * application.yml 的 {@code eduai.paper.*} 默认值。</p>
 *
 * <p>为什么走 {@link GlobalSettingReader#getStrDirect(String, String)}：设置页保存后写的是
 * <b>全局层</b>，而 {@code getStr} 会优先读当前用户的覆盖层并走 Redis 缓存 —— 组卷可能是
 * 后台任务、不一定带登录用户上下文，用合并视图会「页面保存了、组卷却按旧值跑」。
 * 直读全局层与 runtime / paper / marking 三个系统分组同口径。</p>
 *
 * <p>这里不做快照缓存：组卷与替换题目都是低频操作，每次读三项的代价可以忽略，
 * 换来的是「页面保存后立刻生效」，不需要等缓存过期。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperComposeSettingService {

    /**
     * 全局设置分组标识
     */
    private static final String GROUP = "compose";

    /**
     * 字段名：默认题目数量
     */
    private static final String FIELD_QUESTION_COUNT = "default_question_count";

    /**
     * 字段名：默认每题分值
     */
    private static final String FIELD_SCORE_PER_QUESTION = "default_score_per_question";

    /**
     * 字段名：默认卷面总分
     */
    private static final String FIELD_MAX_TOTAL_SCORE = "max_total_score";

    private final EduAiProperties properties;

    /**
     * 全局设置读取器（ai-starter 装配；缺失时全部回落到 YAML 默认值）
     */
    @Autowired(required = false)
    private GlobalSettingReader globalSettingReader;

    /**
     * 默认卷面总分（AI 组卷未显式指定总分时使用）
     *
     * @return 正数总分；配置缺失或非法时回落到 {@code eduai.paper.max-total-score}
     */
    public int maxTotalScore() {
        return readPositiveInt(FIELD_MAX_TOTAL_SCORE, paperDefaults().getMaxTotalScore());
    }

    /**
     * 默认题目数量（AI 组卷未显式指定题量时使用）
     *
     * @return 正数题量；配置缺失或非法时回落到 {@code eduai.paper.default-question-count}
     */
    public int defaultQuestionCount() {
        return readPositiveInt(FIELD_QUESTION_COUNT, paperDefaults().getDefaultQuestionCount());
    }

    /**
     * 默认每题分值（组卷写入题项分值时使用）
     *
     * @return 正数分值；配置缺失或非法时回落到 {@code eduai.paper.default-score-per-question}
     */
    public int defaultScorePerQuestion() {
        return readPositiveInt(FIELD_SCORE_PER_QUESTION, paperDefaults().getDefaultScorePerQuestion());
    }

    /**
     * 读取一个正整数配置项；全局设置读取器缺失、字段为空或值非正数时返回兜底值
     *
     * <p>非正数一律视为非法：题量为 0 会组出空卷，分值 / 总分为 0 会让发布校验永远不通过，
     * 这类脏值不应该顺着页面配置流进业务。</p>
     *
     * @param field        字段名
     * @param defaultValue 兜底值
     * @return 生效值
     */
    private int readPositiveInt(String field, int defaultValue) {
        if (globalSettingReader == null) {
            return defaultValue;
        }
        String value;
        try {
            value = globalSettingReader.getStrDirect(GROUP, field);
        } catch (Exception e) {
            log.debug("[PaperComposeSetting] 读取全局设置失败(field={}): {}", field, e.getMessage());
            return defaultValue;
        }
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("[PaperComposeSetting] 配置项 {} 不是整数: {}，回落到默认值 {}", field, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 取 YAML 侧的组卷默认值；配置缺失时回退 {@link EduAiProperties.Paper} 的字段默认值
     *
     * @return 组卷默认配置，非 null
     */
    private EduAiProperties.Paper paperDefaults() {
        EduAiProperties.Paper paper = properties.getPaper();
        return paper == null ? new EduAiProperties.Paper() : paper;
    }
}
