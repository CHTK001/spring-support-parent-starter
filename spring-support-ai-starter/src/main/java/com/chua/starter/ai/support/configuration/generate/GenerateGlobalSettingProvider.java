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
    /**
     * 分组
    */
    public String group() {
        return GROUP;
    }

    @Override
    /**
     * 分组名称
    */
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    /**
     * 获取Items
    */
    public List<GlobalSettingProvider.SettingItem> getItems() {
        return List.of(
                // ==================== 文生图 ====================
                new SettingItem("image_enabled", "true", "BOOLEAN", "是否启用全局文生图会话（false 时 ImageClient 禁用）", 1),
                new SettingItem("image_provider", "", "STRING", "文生图供应商（zhipu / qwen / baidu / seedream 等）", 2),
                new SettingItem("image_model", "", "STRING", "默认文生图模型", 3),
                new SettingItem("image_base_url", "", "STRING", "文生图 API 基础地址", 4),
                new SettingItem("image_api_key", "", "STRING", "文生图 API Key", 5),
                new SettingItem("image_size", "1024x1024", "STRING", "默认出图尺寸（如 1024x1024）", 6),
                new SettingItem("image_negative_prompt", "", "STRING", "默认负面提示词（留空关闭）", 7),
                new SettingItem("image_device", "cpu", "STRING", "推理设备（cpu / cuda，本地模型）", 8),
                // ==================== 生成文件目录 ====================
                // 落盘层级：{基路径}/{账号}/{image|video}/{上传目录}/{taskId}/…
                // 生成结果落在类型目录下；参考图等上传件落在类型目录的「上传目录」子目录下，
                // 两者分开是为了让「产出」与「上传」在磁盘上一眼可分，备份/清理策略也能不同。
                new SettingItem("media_base_path", "data/generate-media", "STRING", "生成文件根目录（相对工作目录的基路径，其下按 账号 / 类型 / 上传目录 分层）", 9),
                new SettingItem("media_upload_dir", "upload", "STRING", "上传目录名（参考图等上传件在类型目录下的子目录名）", 10),
                // ==================== 文生视频 ====================
                new SettingItem("video_enabled", "true", "BOOLEAN", "是否启用全局文生视频会话（false 时 VideoClient 禁用）", 11),
                new SettingItem("video_provider", "", "STRING", "文生视频供应商（zhipu / qwen / baidu 等）", 12),
                new SettingItem("video_model", "", "STRING", "默认文生视频模型", 13),
                new SettingItem("video_base_url", "", "STRING", "文生视频 API 基础地址", 14),
                new SettingItem("video_api_key", "", "STRING", "文生视频 API Key", 15),
                new SettingItem("video_duration", "5", "NUMBER", "默认视频时长（秒）", 16),
                new SettingItem("video_ratio", "16:9", "STRING", "默认视频比例（16:9 / 9:16 / 1:1）", 17),
                // 生图/生视频的任务超时：原先是代码里写死的 5 分钟（GenerateService.STALE_TASK_MILLIS），
                // 改不了。暴露成配置后，「一条任务卡住」这件事终于能按部署情况调整，
                // 不必改代码重新打包。
                new SettingItem("task_timeout_minutes", "5", "NUMBER",
                        "生成任务超时时间（分钟）：任务创建后超过这么久仍未结束就判为失败，"
                                + "并释放「一次只允许一个在跑」的名额（1-120）", 18)
        );
    }
}
