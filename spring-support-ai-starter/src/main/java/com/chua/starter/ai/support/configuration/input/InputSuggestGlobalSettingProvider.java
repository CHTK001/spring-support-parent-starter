package com.chua.starter.ai.support.configuration.input;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 输入框预测模块全局设置提供者 SPI。
 *
 * <p>定义 {@code input_suggest} 分组的全部配置项。与 {@code input}（输入优化）分开成组，
 * 是因为两者是两件事：输入优化改的是「用户已经打出来的那句话」，
 * 输入框预测给的是「用户下一条可能说的话」，各自的模型、温度、提示词互不影响。</p>
 *
 * <p><b>模型口径（用户需求 1.1：支持配置模型，默认当前模型）</b>：
 * {@code suggest_model} 有值用它；为空时用当前会话/用户选中的模型
 * （由前端随请求传入，后端再兜底到 {@code EffectiveChatModelResolver}）。
 * 含 {@code model} 的字段对普通账号开放（见 {@code SettingsController.USER_MODEL_GROUPS}），
 * 写进自己的覆盖层，不落到全局默认。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Spi("input_suggest")
public class InputSuggestGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "input_suggest";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "输入预测";

    /**
     * 配置分组标识。
     *
     * @return input_suggest
     */
    @Override
    public String group() {
        return GROUP;
    }

    /**
     * 配置分组显示名称。
     *
     * @return 输入预测
     */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
     * 输入预测模块全部配置项。
     *
     * <p>核心链路：suggest_model（预测模型，空=当前会话模型）、suggest_enabled（开关）、
     * suggest_prompt（自定义提示词，空=内置默认）、suggest_temperature（温度）。</p>
     *
     * @return 配置项列表
     */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("suggest_model", "", "STRING",
                        "输入预测模型（空=使用当前会话选中的模型）", 1),
                new SettingItem("suggest_enabled", "true", "BOOLEAN",
                        "是否启用输入框预测（按 Tab 填入建议，不自动发送）", 2),
                new SettingItem("suggest_prompt", "", "STRING",
                        "自定义预测提示词（空=使用内置默认提示词）", 3),
                new SettingItem("suggest_temperature", "0.4", "NUMBER",
                        "预测时的生成温度（越低越稳定）", 4),
                new SettingItem("suggest_context_messages", "8", "NUMBER",
                        "参与预测的最近消息条数（1-20）", 5)
        );
    }
}
