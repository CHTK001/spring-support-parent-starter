// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
     * @return 返回值
    */
    public String group() {
        return GROUP;
    }

    @Override
    /**
     * 分组名称
     * @return 返回值
    */
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    /**
     * 获取Items
     * @return 返回值
    */
    public List<GlobalSettingProvider.SettingItem> getItems() {
        return List.of(
                // ==================== 文生图 ====================
                // 2026-11-10：删掉 image_enabled / image_provider / image_base_url / image_api_key /
                // image_size / image_negative_prompt / image_device 与文生视频的
                // enabled / provider / base_url / api_key / duration / ratio 共 13 项 ——
                // 全仓逐字段扫描确认它们除了本定义外没有任何消费方（真正出图走「模型管理 +
                // 渠道」那套，尺寸取自模型 capabilities[].generate.sizes），留在界面上就是假配置。
                new SettingItem("image_model", "", "STRING", "默认文生图模型", 3),
                // ==================== 生成文件目录 ====================
                // 落盘层级：{基路径}/{账号}/{image|video}/{上传目录}/{taskId}/…
                // 生成结果落在类型目录下；参考图等上传件落在类型目录的「上传目录」子目录下，
                // 两者分开是为了让「产出」与「上传」在磁盘上一眼可分，备份/清理策略也能不同。
                new SettingItem("media_base_path", "data/generate-media", "STRING", "生成文件根目录（相对工作目录的基路径，其下按 账号 / 类型 / 上传目录 分层）", 9),
                new SettingItem("media_upload_dir", "upload", "STRING", "上传目录名（参考图等上传件在类型目录下的子目录名）", 10),
                // ==================== 文生视频 ====================
                new SettingItem("video_model", "", "STRING", "默认文生视频模型", 13),
                // 生图/生视频的任务超时：原先是代码里写死的 5 分钟（GenerateService.STALE_TASK_MILLIS），
                // 改不了。暴露成配置后，「一条任务卡住」这件事终于能按部署情况调整，
                // 不必改代码重新打包。
                new SettingItem("task_timeout_minutes", "5", "NUMBER",
                        "生成任务超时时间（分钟）：任务创建后超过这么久仍未结束就判为失败，"
                                + "并释放「一次只允许一个在跑」的名额（1-120）", 18)
        );
    }
}