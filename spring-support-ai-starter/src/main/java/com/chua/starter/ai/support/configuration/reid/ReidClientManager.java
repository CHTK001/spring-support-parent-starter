package com.chua.starter.ai.support.configuration.reid;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FaceDetector;
import com.chua.deeplearning.support.feature.FeatureExtractor;
import com.chua.deeplearning.support.reid.DefaultPersonTrack;
import com.chua.deeplearning.support.reid.FaceReidPipeline;
import com.chua.deeplearning.support.reid.PersonTrack;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.face.FaceClientManager;
import com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 人脸重识别客户端管理器。
 *
 * <p>管理 {@code reid} 分组配置对应的运行实例：检测管线（复用
 * {@link FaceClientManager} 的共享实例，不重复加载 ONNX 模型）、特征提取器与跨帧轨迹管理器。</p>
 *
 * <p><b>模型共享。</b>重识别与人脸识别用的是同一批 ONNX 模型（SCRFD 检测 + ArcFace 特征）。
 * 检测管线直接取 {@link FaceClientManager} 已构建好的单例 —— 那些 ONNX 模型才是重的部分，
 * 管线本身只是轻量包装，因此共用毫无代价；而各建一份会让同一个模型在内存里出现两次。</p>
 *
 * <p><b>配置热更新。</b>{@code reid} 分组变更后 {@link #rebuild()} 重建轨迹管理器与特征提取器。
 * 注意：轨迹管理器重建会<b>清空已有轨迹</b> —— 因为归并阈值/时间窗变了之后，
 * 旧轨迹与新参数的语义不再一致，留着会得出「昨天的人今天还在场」的荒谬结论。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReidClientManager {

    /**
     * 配置分组。
     */
    private static final String GROUP = "reid";

    /**
     * 默认同人判定阈值。
     */
    private static final float DEFAULT_THRESHOLD = 0.5f;

    /**
     * 默认检测置信度阈值（低阈值，尽量多检出模糊/远处人脸）。
     */
    private static final float DEFAULT_DETECTOR_THRESHOLD = 0.3f;

    /**
     * 默认人脸识别分流阈值：检测置信度 ≥ 此值走人脸识别，否则走重识别。
     */
    private static final float DEFAULT_FACE_THRESHOLD = 0.5f;

    /**
     * 默认归并时间窗（秒）。
     */
    private static final int DEFAULT_GAP_SECONDS = 30;

    /**
     * 默认轨迹容量上限。
     */
    private static final int DEFAULT_MAX_TRACKS = 10000;

    /**
     * 默认轨迹保留时长（秒）；0 表示永久。
     */
    private static final int DEFAULT_TTL_SECONDS = 0;

    /**
     * 全局设置读取器。
     */
    private final GlobalSettingReader settingReader;

    /**
     * 人脸客户端管理器（共享检测模型）。
     */
    private final FaceClientManager faceClientManager;

    /**
     * 当前生效的重识别管线。
     */
    private volatile FaceReidPipeline pipeline;

    /**
     * 当前生效的轨迹管理器。
     */
    private volatile PersonTrack track;

    /**
     * 当前生效的特征提取器。
     */
    private volatile FeatureExtractor featureExtractor;

    /**
     * 当前生效的检测管线。
     */
    private volatile FacePipeline detector;

    /**
     * 当前生效的**低阈值**检测管线（重识别专用）。
     *
     * <p><b>为什么需要单独一份。</b>人脸识别分组的检测阈值是「宁可漏检也不误检」
     * 的高标准（默认 0.5），而重识别需要「尽量多检出」—— 远处、模糊、侧脸
     * 才是重识别真正的主场（清晰人脸走人脸识别即可）。两者对同一批框的取舍相反，
     * 因此重识别用<b>自己的低阈值检测器</b>。</p>
     *
     * <p><b>不重复加载 ONNX 模型</b>：底层 Translator 由引擎按 modelId 缓存，
     * 这里只是换了一个阈值参数重新取一次翻译器，模型权重仍是同一份。</p>
     */
    private volatile FacePipeline lowThresholdDetector;

    /**
     * 当前生效的重识别管线（懒构建）。
     *
     * @return 管线；检测或特征模型未配置时返回 {@code null}
     */
    public FaceReidPipeline currentPipeline() {
        FaceReidPipeline current = pipeline;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (pipeline == null) {
                build();
            }
            return pipeline;
        }
    }

    /**
     * 当前生效的轨迹管理器（懒构建）。
     *
     * @return 轨迹管理器；管线未就绪时返回 {@code null}
     * @deprecated 轨迹按账号隔离，业务侧应使用 {@link #newTrack()} 为每个账号新建一份。
     */
    @Deprecated
    public PersonTrack currentTrack() {
        currentPipeline();
        return track;
    }

    /**
     * 按当前配置新建一个轨迹管理器。
     *
     * <p><b>每个账号一份。</b>轨迹记录的是「这个账号的摄像头看到了谁」，
     * 共享一份会让 A 账号的观测命中 B 账号的轨迹 —— 既是数据越权，
     * 也会产生「认错人」的假阳性。轨迹管理器只存特征与元数据，很轻；
     * 真正重的是 ONNX 模型，那部分由本类单例共享。</p>
     *
     * @return 轨迹管理器
     */
    public PersonTrack newTrack() {
        return new DefaultPersonTrack(
                readThreshold(),
                settingReader.getInt(GROUP, "track_max_tracks", DEFAULT_MAX_TRACKS),
                settingReader.getInt(GROUP, "track_ttl_seconds", DEFAULT_TTL_SECONDS) * 1000L,
                settingReader.getInt(GROUP, "track_gap_seconds", DEFAULT_GAP_SECONDS) * 1000L);
    }

    /**
     * 当前生效的检测管线。
     *
     * @return 检测管线；未就绪时返回 {@code null}
     */
    public FacePipeline currentDetector() {
        currentPipeline();
        return detector;
    }

    /**
     * 当前生效的**低阈值**检测管线（重识别专用）。
     *
     * <p>与 {@link #currentDetector()} 的区别只在<b>检测置信度阈值</b>：
     * 本管线用 {@code reid.detector_threshold}（默认 0.3），尽量多检出；
     * 人脸识别那条链路用 face 组的阈值（默认 0.5），宁可漏检也不误检。</p>
     *
     * @return 低阈值检测管线；未就绪时返回 {@code null}
     */
    public FacePipeline currentLowThresholdDetector() {
        currentPipeline();
        return lowThresholdDetector;
    }

    /**
     * 人脸识别分流阈值：检测置信度 ≥ 此值走人脸识别，否则走重识别。
     *
     * @return 分流阈值
     */
    public float faceThreshold() {
        BigDecimal value = settingReader.getDecimalOrNull(GROUP, "face_threshold");
        return value == null ? DEFAULT_FACE_THRESHOLD : value.floatValue();
    }

    /**
     * 当前生效的特征提取器。
     *
     * @return 特征提取器；未就绪时返回 {@code null}
     */
    public FeatureExtractor currentFeatureExtractor() {
        currentPipeline();
        return featureExtractor;
    }

    /**
     * 重识别能力是否就绪。
     *
     * @return 就绪返回 true
     */
    public boolean available() {
        return currentPipeline() != null;
    }

    /**
     * 全局配置变更监听：reid 分组更新后热重建。
     *
     * @param event 配置变更事件
     */
    @EventListener
    public void onSettingsChanged(FaceSettingsChangedEvent event) {
        if (event == null || !GROUP.equals(event.group())) {
            return;
        }
        if (event.userScoped()) {
            // 共享实例只承载全局配置：按触发者身份重建会把他的模型烘进共享实例、串掉其他用户。
            log.debug("[ReidClientManager] 收到用户级 reid 配置写入，共享实例不重建");
            return;
        }
        log.info("[ReidClientManager] 收到 reid 分组配置变更，开始热重建");
        rebuild();
    }

    /**
     * 配置热更新：按最新配置重建重识别实例。
     *
     * <p><b>重建会清空已有轨迹。</b>归并阈值与时间窗变了之后，旧轨迹是按旧参数归并出来的，
     * 留着会让「按新参数解释旧轨迹」得出错误结论（例如新阈值下本该分开的两条轨迹被当成一条）。
     * 与其留下语义不一致的数据，不如清空重建。</p>
     */
    public synchronized void rebuild() {
        this.pipeline = null;
        this.track = null;
        this.featureExtractor = null;
        this.detector = null;
        this.lowThresholdDetector = null;
        build();
        log.info("[ReidClientManager] 人脸重识别实例热更新完成");
    }

    /**
     * 按当前配置构建重识别实例。
     *
     * <p>本管理器只负责<b>共享</b>的部分：检测管线与特征提取器。
     * 轨迹是按账号隔离的状态，由业务侧用 {@link #newTrack()} 为每个账号单独创建 ——
     * 管理器里不放任何账号的轨迹。</p>
     */
    private void build() {
        FacePipeline sharedDetector = faceClientManager.currentPipeline();
        if (sharedDetector == null) {
            // 人脸识别分组没配检测模型时，用 reid 分组自己的 detector_model 兜底。
            //
            // <p>不兜底会有一个很别扭的现象：重识别配置页明明提供了「检测模型」下拉，
            // 用户选了它、保存成功、页面也显示已配置，能力却依然是 available=false ——
            // 因为检测管线实际取的是 face 分组的单例。实测部署时正是如此：
            // 只配 reid 分组不生效，必须额外去配人脸识别分组才可用。</p>
            //
            // <p>共享 face 的单例仍是首选（那些 ONNX 模型是重的，能复用就不重复加载），
            // 只有在它为 null 时才自建。</p>
            sharedDetector = buildOwnDetector();
        }
        if (sharedDetector == null) {
            log.info("[Reid] 人脸检测模型未配置，重识别能力未启用");
            return;
        }
        String featureModel = settingReader.getStr(GROUP, "feature_model");
        if (StringUtils.isBlank(featureModel)) {
            // 特征模型留空时回落到人脸识别分组的配置：两者用同一批模型，
            // 用户通常只需要配一次；都为空才说明该能力未启用。
            featureModel = settingReader.getStr("face", "feature_model");
        }
        if (StringUtils.isBlank(featureModel)) {
            log.info("[Reid] 人脸特征模型未配置，重识别能力未启用");
            return;
        }
        String device = settingReader.getStr(GROUP, "device");
        FeatureExtractor extractor = FeatureExtractor.create(featureModel);
        if (StringUtils.isNotBlank(device)) {
            extractor = extractor.device(device);
        }

        this.detector = sharedDetector;
        this.featureExtractor = extractor;
        // 低阈值检测管线：重识别专用，尽量多检出模糊/远处人脸。
        // 构建失败时退回共享管线（阈值高一点而已，不影响功能，只是少检出一些）。
        this.lowThresholdDetector = buildLowThresholdDetector(sharedDetector);
        // 管线里的轨迹用一份临时实例：共享实例本身不承载任何账号的轨迹，
        // 业务侧会用各自账号的轨迹管理器另建管线。
        this.track = newTrack();
        this.pipeline = new FaceReidPipeline(this.lowThresholdDetector, extractor, this.track);
        log.info("[Reid] 重识别实例就绪: 特征模型={}, 阈值={}, 归并窗口={}s, 容量={}, 检测阈值={}, 识别分流={}",
                featureModel, readThreshold(),
                settingReader.getInt(GROUP, "track_gap_seconds", DEFAULT_GAP_SECONDS),
                settingReader.getInt(GROUP, "track_max_tracks", DEFAULT_MAX_TRACKS),
                readDetectorThreshold(), faceThreshold());
    }

    /**
     * 构建**低阈值**检测管线。
     *
     * <p><b>为什么要单独一份检测管线。</b>人脸识别分组的检测阈值是「宁可漏检也不误检」
     * （默认 0.5），而重识别要「尽量多检出」—— 远处、模糊、侧脸才是重识别的主场
     * （清晰人脸走人脸识别即可）。两者对同一批框的取舍正好相反，共用一个阈值
     * 就必须二选一。</p>
     *
     * <p><b>不会重复加载 ONNX 权重</b>：底层 Translator 由引擎按 modelId 缓存，
     * 这里只是换了阈值参数再取一次翻译器，模型权重仍是同一份。</p>
     *
     * <p>构建失败时返回入参（共享管线）—— 阈值高一些而已，只是少检出一些模糊脸，
     * 不该因此让整个重识别能力不可用。</p>
     *
     * @param sharedDetector 共享检测管线（兜底用）
     * @return 低阈值检测管线
     */
    private FacePipeline buildLowThresholdDetector(FacePipeline sharedDetector) {
        float threshold = readDetectorThreshold();
        String detectorModel = settingReader.getStr(GROUP, "detector_model");
        if (StringUtils.isBlank(detectorModel)) {
            detectorModel = settingReader.getStr("face", "detector_model");
        }
        if (StringUtils.isBlank(detectorModel)) {
            // 没有独立的检测模型名：无法自建低阈值管线，退回共享的。
            log.debug("[Reid] 未配置独立检测模型，低阈值检测退回共享管线（阈值 {}）", threshold);
            return sharedDetector;
        }
        try {
            // minConfidence 是检测之后的置信度过滤：把「框的置信度 ≥ 阈值」这一层
            // 放到管线里做，于是低阈值管线与共享管线共用同一个 ONNX 翻译器，
            // 只是各用一个置信度下限。
            //
            // 注意：ONNX 翻译器自身的内置阈值（ScrFD 默认 0.70）是**更严**的一层，
            // 改不动 —— 但它作用在模型原始输出上，而 minConfidence 作用在翻译器
            // 已经放行的框上。实际效果：minConfidence 越低，能进入后续分流的框越多。
            FacePipeline built = FacePipeline.builder()
                    .detector(detectorModel)
                    .minConfidence(threshold)
                    .build();
            if (built == null) {
                return sharedDetector;
            }
            log.info("[Reid] 低阈值检测管线就绪: 模型={}, 置信度下限={}", detectorModel, threshold);
            return built;
        } catch (Exception ex) {
            log.warn("[Reid] 构建低阈值检测管线失败（退回共享管线）: model={} -> {}",
                    detectorModel, ex.getMessage());
            return sharedDetector;
        }
    }

    /**
     * 读取检测置信度阈值（低阈值）。
     *
     * @return 阈值
     */
    private float readDetectorThreshold() {
        BigDecimal value = settingReader.getDecimalOrNull(GROUP, "detector_threshold");
        return value == null ? DEFAULT_DETECTOR_THRESHOLD : value.floatValue();
    }

    /**
     * 用 reid 分组自己的 {@code detector_model} 构建检测管线。
     *
     * <p>只在 {@link FaceClientManager#currentPipeline()} 为空时调用 —— 共享实例可用时
     * 优先复用它，避免同一份 ONNX 检测模型被加载两遍。</p>
     *
     * @return 检测管线；未配置或构建失败返回 {@code null}
     */
    private FacePipeline buildOwnDetector() {
        String detectorModel = settingReader.getStr(GROUP, "detector_model");
        if (StringUtils.isBlank(detectorModel)) {
            return null;
        }
        try {
            FaceDetector ownDetector = FaceDetector.create(detectorModel);
            FacePipeline ownPipeline = FacePipeline.builder().detector(ownDetector).build();
            log.info("[Reid] 人脸识别分组未配置检测模型，改用 reid 分组自建: {}", detectorModel);
            return ownPipeline;
        } catch (Exception ex) {
            log.warn("[Reid] 按 reid 分组的 detector_model={} 构建检测管线失败: {}",
                    detectorModel, ex.getMessage());
            return null;
        }
    }

    /**
     * 读取同人判定阈值。
     *
     * @return 阈值
     */
    private float readThreshold() {
        BigDecimal value = settingReader.getDecimalOrNull(GROUP, "track_threshold");
        return value == null ? DEFAULT_THRESHOLD : value.floatValue();
    }

    /**
     * 读取默认摄像头标识。
     *
     * @return 摄像头标识；未配置返回 {@code null}
     */
    public String defaultCameraId() {
        String value = settingReader.getStr(GROUP, "default_camera_id");
        return StringUtils.isBlank(value) ? null : value;
    }

    /**
     * 构建重识别配置摘要（供日志/健康检查）。
     *
     * @return 摘要
     */
    public String configSummary() {
        return "reid{feature=" + settingReader.getStr(GROUP, "feature_model")
                + ", threshold=" + readThreshold()
                + ", gap=" + settingReader.getInt(GROUP, "track_gap_seconds", DEFAULT_GAP_SECONDS) + "s"
                + ", maxTracks=" + settingReader.getInt(GROUP, "track_max_tracks", DEFAULT_MAX_TRACKS)
                + ", device=" + settingReader.getStr(GROUP, "device")
                + ", detectThreshold=" + readDetectorThreshold()
                + ", faceThreshold=" + faceThreshold()
                + ", available=" + available() + "}";
    }
}
