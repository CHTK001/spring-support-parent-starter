package com.chua.starter.eduai.configuration.compose;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * AI 组卷全局配置提供者 SPI。
 *
 * <p>定义 {@code compose} 分组的全部配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表，页面通过 {@code /v2/settings}
 * 统一读写，与 {@code paper}（试卷识别）、{@code marking}（阅卷设置）并列出现在
 * 「试卷配置」里。</p>
 *
 * <p>为什么独立成一个分组，而不是塞进 {@code paper}：</p>
 * <ul>
 *   <li>语义不同 —— {@code paper} 管「把卷面读成文字」，{@code marking} 管「读出来怎么判分」，
 *       本分组管「怎么出一张卷」（题量 / 每题分值 / 卷面总分上限）；</li>
 *   <li>框架限制 —— {@code GlobalSettingInitializer} 按分组名去重，同一分组只认第一个
 *       provider，往 {@code paper} 里追加字段会被整组跳过。</li>
 * </ul>
 *
 * <p>这些字段此前只存在于 application.yml 的 {@code eduai.paper.*}，配置中心里根本没有入口，
 * 导致「试卷配置」页看着像识别设置、和组卷毫无关系。迁到本分组后，页面改的是组卷真正
 * 读取的那份值；yml 仍作为未配置时的兜底。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("compose")
public class PaperComposeGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "compose";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "AI 组卷";

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    public List<GlobalSettingProvider.SettingItem> getItems() {
        return List.of(
                new GlobalSettingProvider.SettingItem("default_question_count", "20", "NUMBER",
                        "默认题目数量（AI 组卷未指定题量时使用）", 1),
                new GlobalSettingProvider.SettingItem("default_score_per_question", "5", "NUMBER",
                        "默认每题分值（AI 组卷未指定单题分值时使用）", 2),
                new GlobalSettingProvider.SettingItem("max_total_score", "100", "NUMBER",
                        "默认卷面总分（AI 组卷未指定总分时使用）", 3)
        );
    }
}
