package com.chua.starter.ai.support.configuration.generate;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 文生图 / 文生视频 全局设置提供者 SPI。
 *
 * <p>定义 {@code generate} 分组的全部配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。</p>
 *
 * <p>该分组为系统内部使用的全局 Bean 配置：全局 {@code ImageClient} 与
 * {@code VideoClient} 会话由此驱动，供文生图 / 文生视频管线在后台调用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi("generate")
public class GenerateGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "generate";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "文生图 / 文生视频";

    @Override
    /** 分组 */
    public String group() {
        return GROUP;
    }

    @Override
    /** 分组名称 */
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    /** 获取Items */
    public List<GlobalSettingProvider.SettingItem> getItems() {
        return List.of(
                // ==================== 文生图 ====================
                new SettingItem("image_enabled", "true", "BOOLEAN", "是否启用全局文生图会话（false 时 ImageClient 禁用）", 1),
                new SettingItem("image_provider", "", "STRING", "文生图供应商（zhipu / qwen / baidu / seedream 等）", 2),
                new SettingItem("image_model", "", "STRING", "默认文生图模型（如 cogview-3-flash / stable-diffusion）", 3),
                new SettingItem("image_base_url", "", "STRING", "文生图 API 基础地址", 4),
                new SettingItem("image_api_key", "", "STRING", "文生图 API Key", 5),
                new SettingItem("image_size", "1024x1024", "STRING", "默认出图尺寸（如 1024x1024）", 6),
                new SettingItem("image_negative_prompt", "", "STRING", "默认负面提示词（留空关闭）", 7),
                new SettingItem("image_device", "cpu", "STRING", "推理设备（cpu / cuda，本地模型）", 8),
                // ==================== 文生视频 ====================
                new SettingItem("video_enabled", "true", "BOOLEAN", "是否启用全局文生视频会话（false 时 VideoClient 禁用）", 9),
                new SettingItem("video_provider", "", "STRING", "文生视频供应商（zhipu / qwen / baidu 等）", 10),
                new SettingItem("video_model", "", "STRING", "默认文生视频模型", 11),
                new SettingItem("video_base_url", "", "STRING", "文生视频 API 基础地址", 12),
                new SettingItem("video_api_key", "", "STRING", "文生视频 API Key", 13),
                new SettingItem("video_duration", "5", "NUMBER", "默认视频时长（秒）", 14),
                new SettingItem("video_ratio", "16:9", "STRING", "默认视频比例（16:9 / 9:16 / 1:1）", 15)
        );
    }
}
