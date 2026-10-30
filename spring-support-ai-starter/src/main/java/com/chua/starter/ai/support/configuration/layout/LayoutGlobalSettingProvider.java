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
                // 默认留空：ONNX 未配置时该模型并不存在，写死具体模型名会在首次调用时直接报错；
                // 空值由 LayoutClientManager.build() 判定为「版面分析未启用」，语义更准确
                new GlobalSettingProvider.SettingItem("layout_model", "", "STRING", "版面分析模型", 1),
                new GlobalSettingProvider.SettingItem("layout_threshold", "0.5", "NUMBER", "版面分析置信度阈值（0~1）", 2),
                new GlobalSettingProvider.SettingItem("reading_order_enabled", "true", "BOOLEAN", "是否启用阅读顺序排序（按 y 升序、x 升序排列版块）", 3),
                new GlobalSettingProvider.SettingItem("ocr_link_enabled", "true", "BOOLEAN", "是否联动 OCR 识别版块文字（关闭时 parse 仅输出版面标签）", 4),
                new GlobalSettingProvider.SettingItem("device", "cpu", "STRING", "推理设备（cpu / cuda）", 5)
                // 已移除（原为无消费方的死配置，待功能接线后再行恢复）：
                // table_enabled / pose_model / pose_threshold / super_resolution_model / super_resolution_enabled
                // 其中 table_enabled 对应的 table-struct 模型已随 OnnxModelRegistrar 一并停用（Translator 类缺失）。
        );
    }
}
