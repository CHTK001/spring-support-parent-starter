package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.face.FaceAlignResult;
import com.chua.deeplearning.support.face.FaceCompareResult;
import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FaceFeaturePipelineResult;
import com.chua.deeplearning.support.face.FaceIdentifyHit;
import com.chua.deeplearning.support.face.FaceSearchHit;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.FaceQualityInfo;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.starter.ai.support.configuration.face.FaceClientManager;
import com.chua.starter.ai.support.service.AiFaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 人脸识别服务实现。
 *
 * <p>基于 {@link FaceClientManager} 获取懒加载的人脸模型实例，全部配置实时生效。
 * 1:1 比对使用余弦相似度（与向量库 COSINE 算法一致）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFaceServiceImpl implements AiFaceService {

    /**
     * 人脸客户端管理器（懒加载 + 热重建）
     */
    private final FaceClientManager faceClientManager;

    /**
     * 人脸检测（单独能力）。
     *
     * @param imageData 图片字节
     * @return 检测命中列表
     */
    @Override
    public List<FaceDetectionHit> detect(byte[] imageData) {
        return faceClientManager.currentPipeline().detect(imageData);
    }

    /**
     * 检测最大人脸（单独能力）。
     *
     * @param imageData 图片字节
     * @return 最大人脸命中，无则 null
     */
    @Override
    public FaceDetectionHit detectLargest(byte[] imageData) {
        return faceClientManager.currentPipeline().detectLargest(imageData);
    }

    /**
     * 人脸检测管线（detect → crop → liveness）。
     *
     * @param imageData 图片字节
     * @return 检测命中列表
     */
    @Override
    public List<FaceDetectionHit> detectPipeline(byte[] imageData) {
        return faceClientManager.currentPipeline().detectPipeline(imageData);
    }

    /**
     * 提取人脸特征向量（单独能力）。
     *
     * @param imageData 图片字节
     * @return 特征向量
     */
    @Override
    public float[] extractFeature(byte[] imageData) {
        FaceFeaturePipelineResult r = faceClientManager.currentPipeline().extractFeatureWithMeta(imageData);
        return r == null || r.feature() == null ? new float[0] : r.feature();
    }

    @Override
    public FaceFeaturePipelineResult extractFeaturePipeline(byte[] imageData) {
        return faceClientManager.currentPipeline().extractFeatureWithMeta(imageData);
    }

    @Override
    public FaceAlignResult align(byte[] imageData) {
        return faceClientManager.currentPipeline().align(imageData);
    }

    /**
     * 1:1 人脸比对（单独能力）。
     *
     * @param imageA 图片 A 字节
     * @param imageB 图片 B 字节
     * @return 相似度（0~1）
     */
    @Override
    public double compare(byte[] imageA, byte[] imageB) {
        // 走完整管线（detect→crop→liveness→align→feature）提取两侧特征后余弦比对
        FaceFeaturePipelineResult a = faceClientManager.currentPipeline().extractFeatureWithMeta(imageA);
        FaceFeaturePipelineResult b = faceClientManager.currentPipeline().extractFeatureWithMeta(imageB);
        if (a == null || b == null || a.feature() == null || b.feature() == null) {
            return 0d;
        }
        return cosineSimilarity(a.feature(), b.feature());
    }

    @Override
    public FaceCompareResult compareWithMeta(byte[] imageA, byte[] imageB) {
        return faceClientManager.currentPipeline().compareWithMeta(imageA, imageB);
    }

    /**
     * 1:1 特征向量比对（单独能力）。
     *
     * @param featureA 特征向量 A
     * @param featureB 特征向量 B
     * @return 相似度（0~1）
     */
    @Override
    public double compareFeature(float[] featureA, float[] featureB) {
        return cosineSimilarity(featureA, featureB);
    }

    /**
     * 登记人脸入库（单独能力，取最大人脸）。
     *
     * @param id        人脸 ID
     * @param imageData 图片字节
     * @param metadata  附加元数据
     * @return 是否登记成功
     */
    @Override
    public boolean enroll(String id, byte[] imageData, Map<String, Object> metadata) {
        float[] feature = extractFeature(imageData);
        return faceClientManager.currentPipeline().enroll(id, feature, metadata, id);
    }

    /**
     * 登记管线（detect → crop → liveness → feature → 入库，取最大人脸）。
     *
     * @param id        人脸 ID
     * @param imageData 图片字节
     * @param metadata  附加元数据
     * @return 是否登记成功
     */
    @Override
    public boolean enrollPipeline(String id, byte[] imageData, Map<String, Object> metadata) {
        return faceClientManager.currentPipeline().enrollPipeline(id, imageData, metadata);
    }

    @Override
    public boolean enrollFeature(String id, float[] feature, Map<String, Object> metadata) {
        return faceClientManager.currentPipeline().enroll(id, feature, metadata, id);
    }

    /**
     * 1:N 人脸识别（单独能力，取最大人脸）。
     *
     * @param imageData 图片字节
     * @return 识别命中（TopK）
     */
    @Override
    public FaceIdentifyHit identify(byte[] imageData) {
        return faceClientManager.currentPipeline().identifyPipelineLargest(imageData);
    }

    /**
     * 识别管线（detect → crop → liveness → feature → 检索，取最大人脸）。
     *
     * @param imageData 图片字节
     * @return 识别命中（TopK）
     */
    @Override
    public FaceIdentifyHit identifyPipeline(byte[] imageData) {
        return faceClientManager.currentPipeline().identifyPipelineLargest(imageData);
    }

    /**
     * 1:N 特征检索（单独能力）。
     *
     * @param feature 特征向量
     * @param topK    返回条数
     * @return 检索命中列表
     */
    @Override
    public List<FaceSearchHit> search(float[] feature, int topK) {
        return faceClientManager.currentVectorStorage().search(feature, topK)
                .stream()
                .map(v -> new FaceSearchHit(
                        v.id(),
                        scoreOf(v),
                        v.data(),
                        v.metadata() == null ? Map.of() : v.metadata(),
                        v.content()))
                .toList();
    }

    /**
     * 核验管线（detect → feature → 与指定库中检索比对，取最大人脸）。
     *
     * @param id        人脸 ID
     * @param imageData 图片字节
     * @return 是否匹配
     */
    @Override
    public boolean verify(String id, byte[] imageData) {
        FaceIdentifyHit hit = faceClientManager.currentPipeline().identifyPipelineLargest(imageData);
        if (hit == null) {
            return false;
        }
        String bestId = hit.bestId();
        return bestId != null && bestId.equals(id);
    }

    /**
     * 活体检测（辅助能力）。
     *
     * @param imageData 裁剪后人脸图片字节
     * @return 是否活体
     */
    @Override
    public boolean isLive(byte[] imageData) {
        if (faceClientManager.currentLiveness() == null) {
            return true;
        }
        return faceClientManager.currentLiveness().isLive(imageData);
    }

    /**
     * 活体分数（辅助能力）。
     *
     * @param imageData 裁剪后人脸图片字节
     * @return 活体分数
     */
    @Override
    public float liveScore(byte[] imageData) {
        if (faceClientManager.currentLiveness() == null) {
            return 1.0f;
        }
        try {
            return faceClientManager.currentLiveness().liveScore(imageData);
        } catch (Exception e) {
            return 0.0f;
        }
    }

    /**
     * 动漫人脸检测（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 检测信息列表
     */
    @Override
    public List<DetectionInfo> animeDetect(byte[] imageData) {
        return faceClientManager.currentAnimeDetector().detectInfo(imageData);
    }

    /**
     * 人脸超分（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 超分后图片字节
     */
    @Override
    public byte[] superResolution(byte[] imageData) {
        return faceClientManager.currentSuperResolution().enhance(imageData);
    }

    /**
     * 人脸修复（辅助能力，CodeFormer）。
     *
     * @param imageData 图片字节
     * @return 修复后图片字节
     */
    @Override
    public byte[] restore(byte[] imageData) {
        return faceClientManager.currentRestorer().enhance(imageData);
    }

    /**
     * 人脸属性分析（辅助能力，年龄/性别/种族）。
     *
     * @param imageData 图片字节
     * @return 分类结果标签
     */
    @Override
    public String analyzeAttributes(byte[] imageData) {
        return faceClientManager.currentAttributeClassifier().classify(imageData);
    }

    /**
     * 人脸表情识别（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 表情标签
     */
    @Override
    public String analyzeEmotion(byte[] imageData) {
        return faceClientManager.currentEmotionClassifier().classify(imageData);
    }

    /**
     * 人脸关键点（辅助能力，106 点）。
     *
     * @param imageData 图片字节
     * @return 关键点坐标
     */
    @Override
    public float[] landmark(byte[] imageData) {
        return faceClientManager.currentLandmarkExtractor().extract(imageData);
    }

    /**
     * 人脸质量评估（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 质量信息
     */
    @Override
    public FaceQualityInfo quality(byte[] imageData) {
        if (faceClientManager.currentQualityAssessor() == null) {
            return null;
        }
        return faceClientManager.currentQualityAssessor().assess(imageData);
    }

    /**
     * 换脸检测（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 是否疑似换脸
     */
    @Override
    public boolean isDeepfake(byte[] imageData) {
        String result = faceClientManager.currentDeepfakeClassifier().classify(imageData);
        if (StringUtils.isBlank(result)) {
            return false;
        }
        String lower = result.toLowerCase();
        return lower.contains("fake") || lower.contains("spoof") || lower.contains("deepfake");
    }

    /**
     * 人脸框坐标检测（返回原始矩形框）。
     *
     * @param imageData 图片字节
     * @return 人脸框列表
     */
    @Override
    public List<PredictRectangle> detectBoxes(byte[] imageData) {
        return faceClientManager.currentPipeline().detectBoxes(imageData);
    }

    /**
     * 枚举可用人脸模型。
     *
     * @return 模型清单（能力分组 → 模型 ID 列表）
     */
    @Override
    public Map<String, List<String>> listModels() {
        return faceClientManager.currentPipeline().listModels();
    }

    /**
     * 余弦相似度计算（0~1）。
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 相似度
     */
    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0d;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0d;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 计算某条入库向量与查询向量的相似度（供 search 结果展示）。
     *
     * <p>向量库实现（Memory/JVector）在 search 时会将相似度写入向量 metadata 的 "score" 键，
     * 此处直接读取，缺失时返回 0。</p>
     *
     * @param v 检索结果向量
     * @return 相似度
     */
    private static double scoreOf(com.chua.common.support.vector.Vector v) {
        if (v.metadata() != null && v.metadata().get("score") instanceof Number n) {
            return n.doubleValue();
        }
        return 0d;
    }
}
