package com.chua.starter.ai.support.configuration.reid;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.face.FacePipeline;
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
        // 管线里的轨迹用一份临时实例：共享实例本身不承载任何账号的轨迹，
        // 业务侧会用各自账号的轨迹管理器另建管线。
        this.track = newTrack();
        this.pipeline = new FaceReidPipeline(sharedDetector, extractor, this.track);
        log.info("[Reid] 重识别实例就绪: 特征模型={}, 阈值={}, 归并窗口={}s, 容量={}",
                featureModel, readThreshold(),
                settingReader.getInt(GROUP, "track_gap_seconds", DEFAULT_GAP_SECONDS),
                settingReader.getInt(GROUP, "track_max_tracks", DEFAULT_MAX_TRACKS));
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
                + ", available=" + available() + "}";
    }
}
