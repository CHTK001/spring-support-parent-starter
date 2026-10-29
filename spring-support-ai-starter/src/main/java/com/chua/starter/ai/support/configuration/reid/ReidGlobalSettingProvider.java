package com.chua.starter.ai.support.configuration.reid;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 人脸重识别模块全局设置提供者 SPI。
 *
 * <p>定义 {@code reid} 分组的全部配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。配置更新写入后，
 * {@link com.chua.starter.ai.support.configuration.reid.ReidClientManager#rebuild()}
 * 热重建运行实例，实时生效。</p>
 *
 * <p><b>与人脸识别（{@code face} 分组）的关系。</b>两者共用同一批 ONNX 模型
 * （检测 + 特征），但重识别多了<b>轨迹</b>维度，因此需要额外的轨迹参数
 * （归并阈值、归并时间窗、轨迹容量、轨迹保留时长），这些在 face 分组里没有对应项。</p>
 *
 * <p><b>双路分流：清晰人脸走人脸识别，模糊人脸走重识别。</b>两者<b>一起运行</b>，
 * 由检测置信度分界（{@code face_threshold}）：</p>
 * <ul>
 *   <li>检测置信度 <b>≥ {@code face_threshold}</b> —— 人脸清晰，走<b>人脸识别</b>
 *       （1:N 定库检索），能直接给出「这是张三」；</li>
 *   <li>检测置信度 <b>&lt; {@code face_threshold}</b> —— 人脸模糊/远处/侧脸，
 *       定库检索不可靠，走<b>重识别</b>（跨摄像头轨迹），回答「这个人去过哪」。</li>
 * </ul>
 * <p>因此检测阈值 {@code detector_threshold} 故意配得比 face 分组更低
 * （默认 0.3）：多检出一些模糊脸没关系，分流之后各自走擅长的路。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("reid")
public class ReidGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "reid";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "人脸重识别";

    /**
     * 配置分组标识。
     *
     * @return reid
     */
    @Override
    public String group() {
        return GROUP;
    }

    /**
     * 配置分组显示名称。
     *
     * @return 人脸重识别
     */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
     * 人脸重识别模块全部配置项。
     *
     * <p>模型类配置默认留空：ONNX 未配置时模型文件并不存在，写死模型名会在首次调用时
     * 直接报错。空值由 {@code ReidClientManager} 判定为「该能力未启用」，
     * 需要时在界面上按需开启。</p>
     *
     * <p>轨迹类配置说明：</p>
     * <ul>
     *   <li>{@code track_threshold} — 同人判定阈值。余弦相似度越高越像，
     *       ArcFace 等深度模型通常同人 ≥ 0.6、不同人 ≤ 0.4，0.5 是常用工作点。</li>
     *   <li>{@code track_gap_seconds} — 归并时间窗。超过此时长的轨迹不再视为
     *       「同一个人还在场」，避免把隔很久的两次出现错误连成一条轨迹。</li>
     *   <li>{@code track_max_tracks} — 轨迹容量上限，超出后淘汰最久未出现的轨迹。</li>
     *   <li>{@code track_ttl_seconds} — 轨迹保留时长，0 表示永久保留。</li>
     * </ul>
     *
     * @return 配置项列表
     */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("detector_model", "", "STRING", "人脸检测模型（重识别需要先检测出人脸；留空则复用人脸识别分组的检测模型）", 1),
                new SettingItem("feature_model", "", "STRING", "人脸特征模型（用于同一人判定；留空则复用人脸识别分组的特征模型）", 2),
                new SettingItem("feature_dimension", "512", "NUMBER", "人脸特征向量维度（需与特征模型匹配；ArcFace/AdaFace 输出 512）", 3),
                new SettingItem("track_threshold", "0.5", "NUMBER", "同人判定阈值（余弦相似度，越高越严格）", 4),
                new SettingItem("track_gap_seconds", "30", "NUMBER", "跨帧归并时间窗（秒）；超过此时长不再并入同一轨迹", 5),
                new SettingItem("track_max_tracks", "10000", "NUMBER", "轨迹容量上限（超出淘汰最久未出现的轨迹）", 6),
                new SettingItem("track_ttl_seconds", "0", "NUMBER", "轨迹保留时长（秒）；0 表示永久保留", 7),
                new SettingItem("top_k", "5", "NUMBER", "以图搜人返回 TopK 条数", 8),
                new SettingItem("match_threshold", "0.5", "NUMBER", "以图搜人命中相似度阈值（低于此值不算命中）", 9),
                new SettingItem("default_camera_id", "", "STRING", "默认摄像头标识（未指定时使用）", 10),
                new SettingItem("device", "cpu", "STRING", "推理设备（cpu / cuda）", 11),
                // ---- 检测阈值：双路分流的分界线 ----
                new SettingItem("detector_threshold", "0.3", "NUMBER",
                        "检测置信度阈值（低阈值，尽量多检出模糊/远处人脸；检测到的人脸按 face_threshold 分流）", 12),
                new SettingItem("face_threshold", "0.5", "NUMBER",
                        "人脸识别分流阈值：检测置信度 ≥ 此值走人脸识别（1:N 定库），低于此值走重识别（轨迹）", 13),
                // ---- 重识别向量库（持久化，跨重启可搜） ----
                new SettingItem("vector_storage", "jvector", "STRING", "重识别向量库（jvector / memory / milvus）", 14),
                new SettingItem("vector_algorithm", "COSINE", "STRING", "向量检索算法（COSINE / EUCLIDEAN / DOT）", 15)
        );
    }
}
