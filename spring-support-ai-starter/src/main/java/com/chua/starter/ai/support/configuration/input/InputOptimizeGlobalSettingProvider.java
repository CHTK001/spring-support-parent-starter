package com.chua.starter.ai.support.configuration.input;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
* 输入优化模块全局设置提供者 SPI。
*
* <p>定义 {@code input} 分组的全部配置项，由 {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
* 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。配置更新通过
* {@link com.chua.starter.ai.support.service.SysAiGlobalSettingService#replace} 写入后，
* {@code InputOptimizeService} 读取最新值，实时生效。</p>
*
* <p>输入优化：在对话/接口调用前，使用大语言模型将用户原始输入改写为更清晰、更适合模型理解的版本。
* 优化模型可在本分组全局配置，未配置时回退到 {@code runtime} 分组默认对话模型。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Spi("input")
public class InputOptimizeGlobalSettingProvider implements GlobalSettingProvider {

    /**
    * 配置分组标识
     */
    private static final String GROUP = "input";

    /**
    * 分组显示名称
     */
    private static final String GROUP_NAME = "输入优化";

    /**
    * 配置分组标识。
    *
    * @return input
     */
    @Override
    public String group() {
        return GROUP;
    }

    /**
    * 配置分组显示名称。
    *
    * @return 输入优化
     */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
    * 输入优化模块全部配置项。
    *
    * <p>核心链路：optimizer_model（优化模型）、optimizer_enabled（开关）、optimizer_prompt（提示词）、
    * 优化器_temperature（温度）。优化器_模型 为空时使用 {@code runtime} 分组的默认对话模型。</p>
    *
    * @return 配置项列表
     */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("optimizer_model", "", "STRING", "输入优化模型（空=使用 runtime 分组默认对话模型，如 gpt-4o）", 1),
                new SettingItem("optimizer_enabled", "true", "BOOLEAN", "是否启用输入优化（对话/接口调用前自动改写输入）", 2),
                new SettingItem("optimizer_prompt", "", "STRING", "自定义优化提示词（空=使用内置默认提示词）", 3),
                new SettingItem("optimizer_temperature", "0.3", "NUMBER", "优化时的生成温度（越低越稳定）", 4)
        );
    }
}
