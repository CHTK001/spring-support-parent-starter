package com.chua.starter.ai.support.configuration.marking;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 阅卷设置全局配置提供者 SPI。
 *
 * <p>定义 {@code marking} 分组的全部配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表，页面通过 {@code /v2/settings}
 * 统一读写，与 {@code paper}（试卷识别）、{@code compose}（AI 组卷）分组并列出现在
 * 「试卷配置」里。</p>
 *
 * <p><b>归属（2026-10-29 收敛）：</b>本类原先住在 {@code spring-support-eduai-starter}，
 * 而渲染 {@code marking} 的「试卷配置」面板是 AI 模块的
 * {@code pages/ai/components/setting/PaperConfigPanel.vue}。分组定义留在业务侧，
 * 会让「只依赖 AI 模块」的宿主的试卷配置缺一组。故迁入本模块，eduai 侧只依赖
 * ai-starter 读取运行时值，不再自行声明分组。</p>
 *
 * <p>为什么独立成一个分组，而不是塞进 {@code paper}：</p>
 * <ul>
 *   <li>语义不同 —— {@code paper} 管「把卷面读成文字」，本分组管「读出来之后怎么判分」，
 *       前者影响识别结果，后者影响分数；</li>
 *   <li>框架限制 —— {@code GlobalSettingInitializer} 按分组名去重，同一分组只认第一个
 *       provider，往 {@code paper} 里追加字段会被整组跳过。</li>
 * </ul>
 *
 * <p>这些字段此前存在 eduai 自己的 {@code edu_app_config} 键值表里，由阅卷工作台的
 * 「阅卷配置」弹窗维护，与 AI 配置中心完全隔离，导致同一个模型的提示词要跑两个入口配置。
 * 迁到本分组后，AI 配置中心与阅卷工作台读的是同一份数据。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("marking")
public class MarkingGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "marking";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "阅卷设置";

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
                // 字段名必须以 _system_prompt 结尾：设置页据此把它渲染成多行文本域
                new GlobalSettingProvider.SettingItem("marking_system_prompt", "", "STRING",
                        "阅卷判分提示词（主观题判分指令，留空用内置默认）", 1),
                // 描述括号里的 strict / loose 会被设置页解析成下拉选项
                new GlobalSettingProvider.SettingItem("match_mode", "strict", "STRING",
                        "匹配模式（strict 严谨 / loose 宽松）", 2),
                new GlobalSettingProvider.SettingItem("extract_strategy", "whole", "STRING",
                        "任意卷识别策略（whole 整卷 / region 按版面区域分块）", 3),
                new GlobalSettingProvider.SettingItem("retry_times", "5", "NUMBER",
                        "模型调用最大重试次数（0 = 不重试）", 4),
                new GlobalSettingProvider.SettingItem("retry_backoff_millis", "1000", "NUMBER",
                        "初始退避毫秒", 5),
                new GlobalSettingProvider.SettingItem("retry_backoff_multiplier", "2", "NUMBER",
                        "退避倍数（<=1 按 2 处理）", 6),
                new GlobalSettingProvider.SettingItem("retry_max_backoff_millis", "20000", "NUMBER",
                        "退避上限毫秒（<=0 按 15000 处理）", 7),
                new GlobalSettingProvider.SettingItem("retry_on_transient", "true", "BOOLEAN",
                        "除限流外是否也重试瞬时故障（超时 / 502 / 503）", 8)
        );
    }
}
