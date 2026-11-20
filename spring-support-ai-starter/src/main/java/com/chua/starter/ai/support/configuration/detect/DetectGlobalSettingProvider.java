package com.chua.starter.ai.support.configuration.detect;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * AI 检测模块全局设置提供者 SPI。
 *
 * <p>定义 {@code detect} 分组的全部配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次），页面通过
 * {@code /v2/settings/detect} 读写（该通用出口对非白名单分组仅管理员可写）。</p>
 *
 * <p>AI 检测是纯 JDK 启发式能力：文本按写作统计特征评分
 * （{@link com.chua.common.support.utils.DetectorUtils}），
 * 图片按元数据（C2PA / XMP / EXIF / 生成参数块）判定
 * （{@link com.chua.common.support.utils.PhotoMetadataUtils}），
 * 不依赖任何模型配置。这里的开关与阈值只控制「是否开放」与「判定口径」。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("detect")
public class DetectGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "detect";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "AI 检测";

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
                new GlobalSettingProvider.SettingItem("text_enabled", "true", "BOOLEAN",
                        "是否启用文本 AI 检测", 1),
                new GlobalSettingProvider.SettingItem("text_threshold", "60", "NUMBER",
                        "文本判定为「很可能 AI 生成」的评分阈值（0~100）", 2),
                new GlobalSettingProvider.SettingItem("image_enabled", "true", "BOOLEAN",
                        "是否启用图片 AI 元数据检测", 3),
                new GlobalSettingProvider.SettingItem("image_max_size_mb", "20", "NUMBER",
                        "单张图片大小上限（MB），超过拒绝检测；0=不限制", 4)
        );
    }
}
