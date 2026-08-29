package com.chua.starter.ai.support.configuration.bot;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 微信 Bot 模块全局设置提供者 SPI。
 *
 * <p>定义 {@code bot} 分组的全部配置项，由 {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。调用层通过
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingReader} 读取。</p>
 *
 * <p>当前提供 Bot 聊天 AI 的默认模型，作为各 Bot 未单独配置模型时的全局兜底，
 * 避免每个用户各自设置模型。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("bot")
public class BotGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "bot";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "Bot";

    /**
     * 配置分组标识。
     *
     * @return bot
     */
    @Override
    public String group() {
        return GROUP;
    }

    /**
     * 配置分组显示名称。
     *
     * @return Bot
     */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
     * Bot 模块全部配置项。
     *
     * @return 配置项列表
     */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("default_model", "", "STRING", "Bot 默认 AI 模型（未单独配置时的全局兜底，模型名须存在于模型管理）", 1)
        );
    }
}