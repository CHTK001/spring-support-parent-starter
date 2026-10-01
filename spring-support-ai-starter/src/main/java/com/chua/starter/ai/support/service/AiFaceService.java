package com.chua.starter.ai.support.service;

import com.chua.deeplearning.support.face.FaceCompareResult;
import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FaceFeaturePipelineResult;
import com.chua.deeplearning.support.face.FaceIdentifyHit;
import com.chua.deeplearning.support.face.FaceSearchHit;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.FaceQualityInfo;
import com.chua.deeplearning.support.model.PredictRectangle;

import java.util.List;
import java.util.Map;

/**
 * AI 人脸识别服务接口。
 *
 * <p>提供人脸识别的单独能力与链式管线能力，全部配置读取自 {@code face} 分组
 * 全局设置（sys_AI_全局_setting），配置变更通过
 * {@link com.chua.starter.ai.support.configuration.face.FaceClientManager#rebuild()} 热更新，实时生效。</p>
 *
 * <p>单独功能：检测、特征提取、登记、1:N 识别、1:1 比对；
 * 管线功能：识别管线（detect→crop→liveness→特征→检索）、登记管线、核验管线；
 * 辅助能力：活体、动漫检测、超分、修复、属性、表情、关键点、质量、换脸检测。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AiFaceService {

    /**
     * 人脸检测（单独能力）。
     *
     * @param imageData 图片字节
     * @return 检测命中列表
     */
    List<FaceDetectionHit> detect(byte[] imageData);

    /**
     * 检测最大人脸（单独能力）。
     *
     * @param imageData 图片字节
     * @return 最大人脸命中，无则 空
     */
    FaceDetectionHit detectLargest(byte[] imageData);

    /**
     * 人脸检测管线（detect → crop → liveness）。
     *
     * @param imageData 图片字节
     * @return 检测命中列表
     */
    List<FaceDetectionHit> detectPipeline(byte[] imageData);

    /**
     * 提取人脸特征向量（单独能力）。
     *
     * @param imageData 图片字节
     * @return 特征向量
     */
    float[] extractFeature(byte[] imageData);

    /**
     * 特征提取完整管线（detect→crop→liveness→align→特征）。
     *
     * @param imageData 图片字节
     * @return 特征管线结果（检测框/裁剪/对齐/活体/特征/耗时），无人脸返回 空
     */
    com.chua.deeplearning.support.face.FaceFeaturePipelineResult extractFeaturePipeline(byte[] imageData);

    /**
     * 人脸对齐管线（detect→crop→liveness→align）。
     *
     * @param imageData 图片字节
     * @return 对齐结果（检测框/裁剪/对齐/活体/耗时），无人脸返回 空
     */
    com.chua.deeplearning.support.face.FaceAlignResult align(byte[] imageData);

    /**
     * 1:1 人脸比对（单独能力）。
     *
     * @param imageA 图片 A 字节
     * @param imageB 图片 B 字节
     * @return 相似度（0~1，越高越相似）
     */
    double compare(byte[] imageA, byte[] imageB);

    /**
     * 1:1 比对完整管线：两侧都跑 detect→crop→liveness→align→特征，再余弦比对。
     *
     * <p>返回两侧的特征提取元数据 + 余弦相似度，便于可观测与调试。</p>
     *
     * @param imageA 图片 A 字节
     * @param imageB 图片 B 字节
     * @return 比对结果（相似度 + A/B 特征管线结果）
     */
    FaceCompareResult compareWithMeta(byte[] imageA, byte[] imageB);

    /**
     * 1:1 特征向量比对（单独能力）。
     *
     * @param featureA 特征向量 A
     * @param featureB 特征向量 B
     * @return 相似度（0~1，越高越相似）
     */
    double compareFeature(float[] featureA, float[] featureB);

    /**
     * 登记人脸入库（单独能力，取最大人脸）。
     *
     * @param id        人脸 标识
     * @param imageData 图片字节
     * @param metadata  附加元数据
     * @return 是否登记成功
     */
    boolean enroll(String id, byte[] imageData, Map<String, Object> metadata);

    /**
     * 登记管线（detect → crop → liveness → align → 特征 → 入库，取最大人脸）。
     *
     * @param id        人脸 标识
     * @param imageData 图片字节
     * @param metadata  元数据
     * @return 是否登记成功
     */
    boolean enrollPipeline(String id, byte[] imageData, Map<String, Object> metadata);

    /**
     * 直接特征入库（不跑管线，调用方自行提取特征后入库）。
     *
     * <p>适用场景：前端先调 {@link #extractFeature(byte[])} 取特征，再调用本接口入库，
     * 或从外部系统导入预计算特征向量。</p>
     *
     * @param id        人脸 标识
     * @param feature   特征向量（维度需与 向量storage 一致）
     * @param metadata  元数据
     * @return 是否成功
     */
    boolean enrollFeature(String id, float[] feature, Map<String, Object> metadata);

    /**
     * 1:N 人脸识别（单独能力，取最大人脸）。
     *
     * @param imageData 图片字节
     * @return 识别命中（TopK）
     */
    FaceIdentifyHit identify(byte[] imageData);

    /**
     * 识别管线（detect → crop → liveness → 特征 → 检索，取最大人脸）。
     *
     * @param imageData 图片字节
     * @return 识别命中（TopK）
     */
    FaceIdentifyHit identifyPipeline(byte[] imageData);

    /**
     * 1:N 特征检索（单独能力）。
     *
     * @param feature 特征向量
     * @param topK    返回条数
     * @return 检索命中列表
     */
    List<FaceSearchHit> search(float[] feature, int topK);

    /**
     * 列出**当前账号**人脸库里已登记的条目（id + 元数据，不含向量）。
     *
     * <p>改造前后端没有列举出口，前端只能自己在浏览器本地存一份「登记簿」——
     * 于是同一份服务端人脸的档案在不同浏览器/账号下看到的并不一致。
     * 快照已经保存了登记时传入的元数据，因此这里可以把它读回来。</p>
     *
     * @return 条目列表（按登记顺序）
     */
    List<Map<String, Object>> list();

    /**
     * 从**当前账号**人脸库里删除一条（向量索引与快照同步删除）。
     *
     * @param id 人脸 标识
     * @return 是否删除成功
     */
    boolean remove(String id);

    /**
     * 核验管线（detect → 特征 → 与指定库中检索比对，取最大人脸）。
     *
     * @param id       人脸 标识
     * @param imageData 图片字节
     * @return 是否匹配
     */
    boolean verify(String id, byte[] imageData);

    /**
     * 活体检测（辅助能力）。
     *
     * @param imageData 裁剪后人脸图片字节
     * @return 是否活体
     */
    boolean isLive(byte[] imageData);

    /**
     * 活体分数（辅助能力）。
     *
     * @param imageData 裁剪后人脸图片字节
     * @return 活体分数
     */
    float liveScore(byte[] imageData);

    /**
     * 动漫人脸检测（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 检测信息列表
     */
    List<DetectionInfo> animeDetect(byte[] imageData);

    /**
     * 人脸超分（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 超分后图片字节
     */
    byte[] superResolution(byte[] imageData);

    /**
     * 人脸修复（辅助能力，编码former）。
     *
     * @param imageData 图片字节
     * @return 修复后图片字节
     */
    byte[] restore(byte[] imageData);

    /**
     * 人脸属性分析（辅助能力，年龄/性别/种族）。
     *
     * @param imageData 图片字节
     * @return 分类结果标签
     */
    String analyzeAttributes(byte[] imageData);

    /**
     * 人脸表情识别（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 表情标签
     */
    String analyzeEmotion(byte[] imageData);

    /**
     * 人脸关键点（辅助能力，106 点）。
     *
     * @param imageData 图片字节
     * @return 关键点坐标
     */
    float[] landmark(byte[] imageData);

    /**
     * 人脸质量评估（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 质量信息
     */
    FaceQualityInfo quality(byte[] imageData);

    /**
     * 换脸检测（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 是否疑似换脸
     */
    boolean isDeepfake(byte[] imageData);

    /**
     * 人脸框坐标检测（返回原始矩形框）。
     *
     * @param imageData 图片字节
     * @return 人脸框列表
     */
    List<PredictRectangle> detectBoxes(byte[] imageData);

    /**
     * 枚举可用人脸模型。
     *
     * @return 模型清单（模型 标识 + 说明）
     */
    Map<String, List<String>> listModels();
}
