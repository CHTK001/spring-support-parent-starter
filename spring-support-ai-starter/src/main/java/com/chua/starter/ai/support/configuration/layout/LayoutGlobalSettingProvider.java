package com.chua.starter.ai.support.configuration.layout;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
* 布局识别模块全局设置提供者 SPI。
*
* <p>定义 {@code layout} 分组的全部配置项，由
* {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
* 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。</p>
*
* <p>布局识别管线：layout(版面分析/文档结构) → pose(姿态估计) → ocr 联动（表格/阅读顺序）。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Spi("layout")
public class LayoutGlobalSettingProvider implements GlobalSettingProvider {

    /**
    * 配置分组标识
    */
    private static final String GROUP = "layout";

    /**
    * 分组显示名称
    */
    private static final String GROUP_NAME = "布局识别";

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
                new GlobalSettingProvider.SettingItem("layout_model", "pp-structurev2-layout", "STRING", "版面分析模型（pp-structurev2-layout / pp-layout-parser / yolov8-doclayout）", 1),
                new GlobalSettingProvider.SettingItem("layout_threshold", "0.5", "NUMBER", "版面分析置信度阈值（0~1）", 2),
                new GlobalSettingProvider.SettingItem("table_enabled", "true", "BOOLEAN", "是否启用表格结构识别", 3),
                new GlobalSettingProvider.SettingItem("reading_order_enabled", "true", "BOOLEAN", "是否启用阅读顺序排序", 4),
                new GlobalSettingProvider.SettingItem("pose_model", "", "STRING", "姿态估计模型（如 yolov8-pose / hrnet，留空关闭）", 5),
                new GlobalSettingProvider.SettingItem("pose_threshold", "0.5", "NUMBER", "姿态估计置信度阈值（0~1）", 6),
                new GlobalSettingProvider.SettingItem("ocr_link_enabled", "true", "BOOLEAN", "是否联动 OCR 识别版块文字", 7),
                new GlobalSettingProvider.SettingItem("super_resolution_model", "", "STRING", "版块图像超分模型（如 real-esrgan / gfpgan，留空关闭）", 8),
                new GlobalSettingProvider.SettingItem("super_resolution_enabled", "false", "BOOLEAN", "是否启用超分能力", 9),
                new GlobalSettingProvider.SettingItem("device", "cpu", "STRING", "推理设备（cpu / cuda）", 10)
        );
    }
}
