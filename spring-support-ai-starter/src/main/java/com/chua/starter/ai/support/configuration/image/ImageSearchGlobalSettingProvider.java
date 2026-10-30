package com.chua.starter.ai.support.configuration.image;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 以图搜图模块全局设置提供者 SPI。
 *
 * <p>定义 {@code image_search} 分组的配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时注册到 {@code sys_ai_global_setting} 表（仅首次），并出现在
 * 「系统设置 → 默认模型」页面，供用户直接选择特征模型。</p>
 *
 * <p><b>只暴露代码真正读取的字段</b>：分组里曾经存在的 {@code max_image_size} 与
 * {@code face_enhance_enabled} 在实现中并未生效，暴露出去会造成「改了配置没反应」
 * 的误解，故不在此声明（历史数据仍保留在库中，不影响读取）。
 * {@code feature_dimension} 同属此类：维度由所选模型的实际输出决定，配置值只被用来
 * 打一条告警，改了不会有任何效果。已一并移除，库中的历史行由升级脚本清理。</p>
 *
 * <p>检索链路：上传图片 → {@code default_image_feature_model} 抽取特征 →
 * {@code vector_storage_engine} 指定的向量库写入/检索 → 按 {@code min_similarity}
 * 过滤命中。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi("image_search")
public class ImageSearchGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "image_search";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "以图搜图";

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
                // 默认留空：ONNX 未配置该模型时视为「以图搜图未启用」，
                // 与 ocr / face 分组口径一致；空值由 ImageSearchStore 判定并给出明确提示
                new GlobalSettingProvider.SettingItem("default_image_feature_model", "", "STRING",
                        "图像特征模型（ONNX 本地推理的模型标识，留空表示未启用）", 1),
                new GlobalSettingProvider.SettingItem("vector_storage_engine", "jvector", "STRING",
                        "向量存储实现（jvector / memory 等，须与向量存储 SPI 已注册名一致）", 2),
                // 0.76 来自真实照片标定：同类最低 0.8184、异类最高 0.6920，取中值。
                // 更换特征模型后该阈值需重新标定，否则会过松或过紧。
                new GlobalSettingProvider.SettingItem("min_similarity", "0.76", "NUMBER",
                        "检索相似度下限（0~1）；默认值按 cn-clip-rn50 真实照片标定，换模型需重新标定", 3),
                // 原有一个 feature_dimension=1024 的配置项，已移除。
                // 原因：它不驱动任何逻辑——向量维度由所选特征模型的实际输出决定，
                // 配置值只被用来打一条「与模型不一致」的告警，用户改了不会有任何效果，
                // 属于典型「改了没反应」的误导性配置。
                // （人脸分组同名项不在此列：FaceClientManager 真的用它建索引，那项保留。）
                // 原先由前端写死 30、后端 defaultValue 再写死一次，属于「不来自默认模型」
                // 的参数。归入本分组后，用户改设置即刻生效，不必改代码重新打包。
                new GlobalSettingProvider.SettingItem("search_top_k", "30", "NUMBER",
                        "单次检索返回的相似图片条数上限（1~200）", 4),
                // 关键词提取与 OCR 互斥：keyword_extractor 只允许 none / ocr / model 之一。
                // model 走模型管理中的 vLLM / OpenAI 兼容多模态模型（keyword_model），
                // 与本地图像描述的 image.captioning_model 是两套能力，不在此混用。
                new GlobalSettingProvider.SettingItem("keyword_extractor", "none", "STRING",
                        "图片关键词来源（none 关闭 / ocr 使用 OCR / model 使用多模态模型提取，与 OCR 互斥）", 5),
                new GlobalSettingProvider.SettingItem("keyword_model", "", "STRING",
                        "图片信息提取模型（vLLM / OpenAI 兼容多模态；keyword_extractor=model 时使用）", 6),
                new GlobalSettingProvider.SettingItem("keyword_prompt", "", "STRING",
                        "图片关键词提取提示词（留空使用默认提示词）", 7)
        );
    }
}