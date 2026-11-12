package com.chua.starter.ai.support.configuration.sim;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 剧本模拟（AI 编剧）全局设置提供者 SPI。
 *
 * <p>定义 {@code sim} 分组的全部配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。</p>
 *
 * <p>该分组只管剧本模拟的「落盘位置」与「编剧行为」：幕次/故事片段/媒体素材的根目录
 * 与编剧侧的默认提示词，均可在设置页（系统设置 → 文生图/文生视频 后的「剧本模拟」组）
 * 热更新，客户端无感知。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("sim")
public class SimGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "sim";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "剧本模拟";

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
                // ==================== 落盘目录 ====================
                // 幕次/故事片段/演员素材统一落地：全局唯一根目录。
                // 相对路径默认 "worlds"，即 /data/ai-starter/worlds；在设置页改一次，
                // 所有用户、所有世界共享同一份剧本文件，同步生效。
                new SettingItem("story_base_path", "worlds", "STRING",
                        "剧本模拟数据根目录（相对工作目录，默认 worlds → 每个世界：{根}/worlds/{worldId}/acts/{故事片段}）", 1),
                new SettingItem("story_mkdir", "true", "BOOLEAN",
                        "世界数据目录不存在时自动创建（true 时自动建目录；false 时写文件失败）", 2),
                new SettingItem("story_max_acts", "64", "NUMBER",
                        "单世界最大幕次：到达上限后拒绝执笔新幕（创建报参数错误），"
                                + "自动补幕则停在最后一幕继续重播，避免剧本无限增长", 3),
                // ==================== 舞台出片 ====================
                // 一幕的「画面」怎么来：video = 让 AI 按分镜出视频（图生视频优先，失败退回文生视频），
                // 舞台连播；image = 只出场景图，舞台用图文演出。关掉视频等于把每一幕省成一两张图。
                new SettingItem("media_mode", "video", "STRING",
                        "舞台出片方式：video = AI 生成分镜视频并连播；image = 只出场景图 + 文字演出", 4),
                // 留空则由服务端在同类型（video）已启用的模型里自动挑一个：
                // 写死一个模型名会让换供应商后整幕素材全失败。
                new SettingItem("video_model", "", "STRING",
                        "视频生成模型（留空自动挑一个可用的；选支持参考图的模型即可图生视频）", 5),
                new SettingItem("image_model", "", "STRING",
                        "图片生成模型（留空自动挑一个可用的；图生视频的首帧就来自它）", 6)
        );
    }
}
