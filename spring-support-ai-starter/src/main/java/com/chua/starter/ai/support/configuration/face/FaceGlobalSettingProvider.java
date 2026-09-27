package com.chua.starter.ai.support.configuration.face;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 人脸模块全局设置提供者 SPI。
 *
 * <p>定义 {@code face} 分组的全部配置项，由 {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。配置更新通过
 * {@link com.chua.starter.ai.support.service.SysAiGlobalSettingService#replace} 写入后，
 * {@code FaceClientManager.rebuild()} 热重建运行实例，实时生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("face")
public class FaceGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "face";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "人脸识别";

    /**
     * 配置分组标识。
     *
     * @return face
     */
    @Override
    public String group() {
        return GROUP;
    }

    /**
     * 配置分组显示名称。
     *
     * @return 人脸识别
     */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
     * 人脸模块全部配置项。
     *
     * <p>核心链路（必填）：detector / feature / vector_storage / vector_algorithm / 阈值 / topK。
     * 辅助能力（选填）：活体 / 质量 / 动漫 / 超分 / 修复 / 属性 / 表情 / 关键点 / 换脸检测。</p>
     *
     * @return 配置项列表
     */
    @Override
    public List<SettingItem> getItems() {
        // 人脸相关模型默认全部留空：ONNX 未配置时模型文件并不存在，写死模型名会在首次调用时直接报错。
        // 空值由 FaceClientManager 判定为「该能力未启用」（返回 null），需要时在界面上按需开启。
        return List.of(
                new SettingItem("detector_model", "", "STRING", "人脸检测模型", 1),
                new SettingItem("detector_threshold", "0.5", "NUMBER", "人脸检测置信度阈值（过滤低置信度框，0~1）", 29),
                new SettingItem("nms_threshold", "0.4", "NUMBER", "人脸检测 NMS 阈值（去重重叠框，0~1）", 30),
                new SettingItem("feature_model", "", "STRING", "人脸特征模型", 2),
                new SettingItem("feature_dimension", "512", "NUMBER", "人脸特征向量维度（需与特征模型匹配；ArcFace/AdaFace 输出 512，InsightFace 3D68 输出 3309）", 21),
                new SettingItem("vector_storage", "jvector", "STRING", "人脸向量库（jvector / memory / milvus）", 3),
                new SettingItem("vector_algorithm", "COSINE", "STRING", "向量检索算法（COSINE / EUCLIDEAN / DOT）", 4),
                new SettingItem("compare_threshold", "0.5", "NUMBER", "1:1 人脸比对相似度阈值", 5),
                new SettingItem("match_threshold", "0.5", "NUMBER", "1:N 人脸检索相似度阈值", 6),
                new SettingItem("top_k", "5", "NUMBER", "1:N 检索返回 TopK 条数", 7),
                new SettingItem("min_face_size", "20", "NUMBER", "最小人脸尺寸（像素）", 8),
                new SettingItem("liveness_model", "", "STRING", "活体检测模型（留空关闭）", 9),
                new SettingItem("liveness_enabled", "false", "BOOLEAN", "是否强制活体（requireLive，liveness_model 配置后生效）", 10),
                new SettingItem("liveness_threshold", "0.5", "NUMBER", "活体分数阈值", 11),
                new SettingItem("quality_model", "", "STRING", "人脸质量评估模型（留空关闭；必须选择质量评估类模型，不可填检测模型）", 31),
                new SettingItem("quality_enabled", "false", "BOOLEAN", "是否启用质量评估（模糊/人脸占比过滤）", 12),
                new SettingItem("anime_detector_model", "", "STRING", "动漫人脸检测模型", 13),
                new SettingItem("anime_enabled", "false", "BOOLEAN", "是否启用动漫人脸检测", 22),
                new SettingItem("super_resolution_model", "", "STRING", "人脸超分模型", 14),
                new SettingItem("super_resolution_enabled", "false", "BOOLEAN", "是否启用超分能力", 23),
                new SettingItem("restore_model", "", "STRING", "人脸修复模型", 15),
                new SettingItem("restore_enabled", "false", "BOOLEAN", "是否启用修复能力", 24),
                new SettingItem("attribute_model", "", "STRING", "人脸属性模型", 16),
                new SettingItem("attribute_enabled", "false", "BOOLEAN", "是否启用属性分析", 25),
                new SettingItem("emotion_model", "", "STRING", "人脸表情模型", 17),
                new SettingItem("emotion_enabled", "false", "BOOLEAN", "是否启用表情识别", 26),
                new SettingItem("landmark_model", "", "STRING", "人脸关键点模型", 18),
                new SettingItem("landmark_enabled", "false", "BOOLEAN", "是否启用关键点定位", 27),
                new SettingItem("deepfake_model", "", "STRING", "换脸检测模型", 19),
                new SettingItem("deepfake_enabled", "false", "BOOLEAN", "是否启用换脸检测", 28),
                new SettingItem("device", "cpu", "STRING", "推理设备（cpu / cuda）", 20)
        );
    }
}
