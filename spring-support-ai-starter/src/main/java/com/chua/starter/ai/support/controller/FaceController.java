package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FaceIdentifyHit;
import com.chua.deeplearning.support.face.FaceSearchHit;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.FaceQualityInfo;
import com.chua.starter.ai.support.service.AiFaceService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * AI 人脸识别控制器。
 *
 * <p>提供人脸识别单独能力、链式管线能力与辅助能力的对外接口，
 * 全部配置读取自 {@code face} 分组全局设置，配置变更实时生效。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/face")
@RequiredArgsConstructor
@Tag(name = "AI 人脸识别", description = "人脸检测、特征、1:1 比对、1:N 识别、活体、动漫、超分、修复、属性、表情、关键点、质量、换脸检测")
public class FaceController {

    /**
     * 人脸识别服务
     */
    private final AiFaceService faceService;

    /**
     * 人脸检测（管线：detect → crop → liveness）。
     *
     * @param file 图片文件
     * @return 人脸检测结果
     */
    @Permission("admin")
    @Operation(summary = "人脸检测", description = "检测图片中的人脸位置、数量与活体状态")
    @PostMapping("/detect")
    public ReturnResult<?> detect(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            List<FaceDetectionHit> hits = faceService.detectPipeline(file.getBytes());
            return ReturnResult.ok(Map.of(
                    "count", hits.size(),
                    "faces", hits
            ));
        } catch (Exception e) {
            log.error("[Face] 人脸检测失败", e);
            return ReturnResult.error("人脸检测失败: " + e.getMessage());
        }
    }

    /**
     * 人脸特征提取。
     *
     * @param file 图片文件
     * @return 特征向量
     */
    @Permission("admin")
    @Operation(summary = "人脸特征提取", description = "提取图片中人脸的特征向量")
    @PostMapping("/feature")
    public ReturnResult<?> feature(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            float[] feature = faceService.extractFeature(file.getBytes());
            return ReturnResult.ok(Map.of("feature", feature));
        } catch (Exception e) {
            log.error("[Face] 特征提取失败: {}", e.getMessage());
            return ReturnResult.error("特征提取失败: " + e.getMessage());
        }
    }

    /**
     * 1:1 人脸比对。
     *
     * @param fileA 图片 A
     * @param fileB 图片 B
     * @return 相似度与判定
     */
    @Permission("admin")
    @Operation(summary = "人脸比对", description = "1:1 人脸比对，返回相似度与是否同一人")
    @PostMapping("/compare")
    public ReturnResult<?> compare(@RequestParam("fileA") MultipartFile fileA,
                                   @RequestParam("fileB") MultipartFile fileB) {
        if (fileA.isEmpty() || fileB.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            double score = faceService.compare(fileA.getBytes(), fileB.getBytes());
            return ReturnResult.ok(Map.of(
                    "score", score,
                    "matched", score >= 0.5
            ));
        } catch (Exception e) {
            log.error("[Face] 人脸比对失败: {}", e.getMessage());
            return ReturnResult.error("人脸比对失败: " + e.getMessage());
        }
    }

    /**
     * 人脸登记入库（1:N）。
     *
     * @param id       人脸 ID
     * @param file     图片文件
     * @param metadata 附加元数据（JSON）
     * @return 登记结果
     */
    @Permission("admin")
    @Operation(summary = "人脸登记", description = "登记人脸特征向量到人脸库")
    @PostMapping("/enroll")
    public ReturnResult<?> enroll(@RequestParam("id") String id,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "metadata", required = false) String metadata) {
        if (id == null || id.isBlank()) {
            return ReturnResult.error("人脸 ID 不能为空");
        }
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            Map<String, Object> meta = parseMetadata(metadata);
            boolean success = faceService.enrollPipeline(id, file.getBytes(), meta);
            return success ? ReturnResult.ok("登记成功") : ReturnResult.error("登记失败");
        } catch (Exception e) {
            log.error("[Face] 人脸登记失败: {}", e.getMessage());
            return ReturnResult.error("人脸登记失败: " + e.getMessage());
        }
    }

    /**
     * 1:N 人脸识别。
     *
     * @param file 图片文件
     * @return 识别结果（TopK 命中）
     */
    @Permission("admin")
    @Operation(summary = "人脸识别", description = "1:N 人脸识别，返回 TopK 命中")
    @PostMapping("/identify")
    public ReturnResult<?> identify(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            FaceIdentifyHit hit = faceService.identifyPipeline(file.getBytes());
            if (hit == null) {
                return ReturnResult.ok(Map.of("matched", false, "hits", List.of()));
            }
            return ReturnResult.ok(Map.of(
                    "matched", hit.bestId() != null,
                    "bestId", hit.bestId(),
                    "bestScore", hit.bestScore(),
                    "hits", hit.hits() == null ? List.of() : hit.hits()
            ));
        } catch (Exception e) {
            log.error("[Face] 人脸识别失败: {}", e.getMessage());
            return ReturnResult.error("人脸识别失败: " + e.getMessage());
        }
    }

    /**
     * 1:N 特征检索。
     *
     * @param feature 特征向量（JSON 数组）
     * @param topK    TopK
     * @return 检索结果
     */
    @Permission("admin")
    @Operation(summary = "人脸检索", description = "按特征向量检索人脸库")
    @GetMapping("/search")
    public ReturnResult<?> search(@RequestParam("feature") String feature,
                                  @RequestParam(value = "topK", defaultValue = "5") int topK) {
        try {
            float[] vector = parseFeature(feature);
            List<FaceSearchHit> hits = faceService.search(vector, topK);
            return ReturnResult.ok(Map.of("hits", hits));
        } catch (Exception e) {
            log.error("[Face] 人脸检索失败: {}", e.getMessage());
            return ReturnResult.error("人脸检索失败: " + e.getMessage());
        }
    }

    /**
     * 核验（人脸 ID + 图片是否匹配）。
     *
     * @param id   人脸 ID
     * @param file 图片文件
     * @return 核验结果
     */
    @Permission("admin")
    @Operation(summary = "人脸核验", description = "核验指定人脸 ID 与图片是否匹配")
    @PostMapping("/verify")
    public ReturnResult<?> verify(@RequestParam("id") String id,
                                  @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            boolean matched = faceService.verify(id, file.getBytes());
            return ReturnResult.ok(Map.of("matched", matched));
        } catch (Exception e) {
            log.error("[Face] 人脸核验失败: {}", e.getMessage());
            return ReturnResult.error("人脸核验失败: " + e.getMessage());
        }
    }

    /**
     * 活体检测。
     *
     * @param file 裁剪后人脸图片
     * @return 活体判定
     */
    @Permission("admin")
    @Operation(summary = "活体检测", description = "检测人脸是否为活体")
    @PostMapping("/liveness")
    public ReturnResult<?> liveness(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            boolean live = faceService.isLive(file.getBytes());
            float score = faceService.liveScore(file.getBytes());
            return ReturnResult.ok(Map.of("live", live, "score", score));
        } catch (Exception e) {
            log.error("[Face] 活体检测失败: {}", e.getMessage());
            return ReturnResult.error("活体检测失败: " + e.getMessage());
        }
    }

    /**
     * 动漫人脸检测。
     *
     * @param file 图片文件
     * @return 动漫人脸检测结果
     */
    @Permission("admin")
    @Operation(summary = "动漫人脸检测", description = "检测动漫/二次元图片中的人脸")
    @PostMapping("/anime-detect")
    public ReturnResult<?> animeDetect(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            List<DetectionInfo> results = faceService.animeDetect(file.getBytes());
            return ReturnResult.ok(Map.of("count", results.size(), "faces", results));
        } catch (Exception e) {
            log.error("[Face] 动漫人脸检测失败: {}", e.getMessage());
            return ReturnResult.error("动漫人脸检测失败: " + e.getMessage());
        }
    }

    /**
     * 人脸超分。
     *
     * @param file 图片文件
     * @return 超分后图片
     */
    @Permission("admin")
    @Operation(summary = "人脸超分", description = "GFPGAN/ESRGAN 人脸超分辨率")
    @PostMapping(value = "/super-resolution", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> superResolution(@RequestParam("file") MultipartFile file) {
        try {
            byte[] result = faceService.superResolution(file.getBytes());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(result);
        } catch (Exception e) {
            log.error("[Face] 人脸超分失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 人脸修复。
     *
     * @param file 图片文件
     * @return 修复后图片
     */
    @Permission("admin")
    @Operation(summary = "人脸修复", description = "CodeFormer 人脸修复（老照片/模糊人脸）")
    @PostMapping(value = "/restore", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> restore(@RequestParam("file") MultipartFile file) {
        try {
            byte[] result = faceService.restore(file.getBytes());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(result);
        } catch (Exception e) {
            log.error("[Face] 人脸修复失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 人脸属性分析。
     *
     * @param file 图片文件
     * @return 年龄/性别/种族
     */
    @Permission("admin")
    @Operation(summary = "人脸属性分析", description = "年龄/性别/种族属性识别")
    @PostMapping("/attributes")
    public ReturnResult<?> attributes(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            String result = faceService.analyzeAttributes(file.getBytes());
            return ReturnResult.ok(Map.of("attributes", result));
        } catch (Exception e) {
            log.error("[Face] 人脸属性分析失败: {}", e.getMessage());
            return ReturnResult.error("人脸属性分析失败: " + e.getMessage());
        }
    }

    /**
     * 人脸表情识别。
     *
     * @param file 图片文件
     * @return 表情标签
     */
    @Permission("admin")
    @Operation(summary = "人脸表情识别", description = "识别图片中的人脸表情")
    @PostMapping("/emotion")
    public ReturnResult<?> emotion(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            String result = faceService.analyzeEmotion(file.getBytes());
            return ReturnResult.ok(Map.of("emotion", result));
        } catch (Exception e) {
            log.error("[Face] 人脸表情识别失败: {}", e.getMessage());
            return ReturnResult.error("人脸表情识别失败: " + e.getMessage());
        }
    }

    /**
     * 人脸关键点。
     *
     * @param file 图片文件
     * @return 关键点坐标（106 点）
     */
    @Permission("admin")
    @Operation(summary = "人脸关键点", description = "人脸 106 点关键点定位")
    @PostMapping("/landmark")
    public ReturnResult<?> landmark(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            float[] points = faceService.landmark(file.getBytes());
            return ReturnResult.ok(Map.of("points", points));
        } catch (Exception e) {
            log.error("[Face] 人脸关键点失败: {}", e.getMessage());
            return ReturnResult.error("人脸关键点失败: " + e.getMessage());
        }
    }

    /**
     * 人脸质量评估。
     *
     * @param file 图片文件
     * @return 质量评估结果
     */
    @Permission("admin")
    @Operation(summary = "人脸质量评估", description = "评估人脸图片质量（模糊/亮度/人脸占比）")
    @PostMapping("/quality")
    public ReturnResult<?> quality(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            FaceQualityInfo info = faceService.quality(file.getBytes());
            if (info == null) {
                return ReturnResult.ok(Map.of("enabled", false));
            }
            return ReturnResult.ok(Map.of(
                    "enabled", true,
                    "faceCount", info.faceCount(),
                    "faceAreaRatio", info.faceAreaRatio(),
                    "blurScore", info.blurScore(),
                    "brightness", info.brightness(),
                    "faceOk", info.faceOk()
            ));
        } catch (Exception e) {
            log.error("[Face] 人脸质量评估失败: {}", e.getMessage());
            return ReturnResult.error("人脸质量评估失败: " + e.getMessage());
        }
    }

    /**
     * 换脸检测。
     *
     * @param file 图片文件
     * @return 是否疑似换脸
     */
    @Permission("admin")
    @Operation(summary = "换脸检测", description = "检测图片是否疑似 AI 换脸")
    @PostMapping("/deepfake")
    public ReturnResult<?> deepfake(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            boolean result = faceService.isDeepfake(file.getBytes());
            return ReturnResult.ok(Map.of("deepfake", result));
        } catch (Exception e) {
            log.error("[Face] 换脸检测失败: {}", e.getMessage());
            return ReturnResult.error("换脸检测失败: " + e.getMessage());
        }
    }

    /**
     * 人脸模型枚举。
     *
     * @return 可用模型清单
     */
    @Permission("admin")
    @Operation(summary = "人脸模型清单", description = "枚举可用的人脸检测/特征/活体/超分等模型")
    @GetMapping("/models")
    public ReturnResult<?> models() {
        return ReturnResult.ok(faceService.listModels());
    }

    /**
     * 解析 JSON 数组特征向量。
     *
     * @param json JSON 数组字符串
     * @return 特征向量
     */
    private static float[] parseFeature(String json) {
        String trimmed = json.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String[] parts = trimmed.substring(1, trimmed.length() - 1).split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        }
        throw new IllegalArgumentException("特征向量格式错误，应为 JSON 数组");
    }

    /**
     * 解析可选的 metadata JSON。
     *
     * @param json JSON 对象字符串，空或非法时返回空 Map
     * @return 元数据 Map
     */
    private static Map<String, Object> parseMetadata(String json) {
        if (null == json || json.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
