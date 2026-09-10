package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.starter.ai.support.configuration.image.ImageRecognitionClientManager;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 图片识别控制器。
 *
 * <p>提供图片分析执行能力（图像分类、目标检测、图像描述、超分修复），
 * 全部配置读取自 {@code image} 分组全局设置，未配置的能力自动降级（返回 null 字段），
 * 配置变更实时生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/image-recognition")
@RequiredArgsConstructor
@Tag(name = "AI 图片识别", description = "图像分类、目标检测、图像描述、超分修复、模型清单")
public class ImageRecognitionController {

    /**
     * 图片识别客户端管理器
     */
    private final ImageRecognitionClientManager recognitionClientManager;

    /**
     * 图片综合分析（分类 + 检测 + 描述，未配置的能力为 null）。
     *
     * @param file 图片文件
     * @param topK 分类返回类别数
     * @return 综合分析结果
     */
    @Permission("admin")
    @Operation(summary = "图片综合分析", description = "按已配置能力执行图像分类/目标检测/图像描述，未配置的能力返回 null")
    @PostMapping("/analyze")
    public ReturnResult<?> analyze(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "topK", defaultValue = "5") int topK) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            long start = System.currentTimeMillis();
            byte[] data = file.getBytes();
            List<DetectionInfo> classification = recognitionClientManager.classifyTopK(data, topK);
            List<DetectionInfo> detections = recognitionClientManager.detect(data);
            String caption = recognitionClientManager.caption(data);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("classification", classification);
            result.put("detections", detections);
            result.put("caption", caption);
            result.put("elapsedMs", System.currentTimeMillis() - start);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("[ImageRecognition] 图片分析失败", e);
            return ReturnResult.error("图片分析失败: " + e.getMessage());
        }
    }

    /**
     * 图像超分修复。
     *
     * @param file 图片文件
     * @return 超分后 PNG 图片
     */
    @Permission("admin")
    @Operation(summary = "图像超分修复", description = "对图片执行超分辨率修复，需先在设置启用 image 分组的超分能力")
    @PostMapping(value = "/super-resolution", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> superResolution(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            byte[] result = recognitionClientManager.superResolution(file.getBytes());
            if (result == null) {
                // 409：超分能力未启用（未配置模型或未开启开关）
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new byte[0]);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(result);
        } catch (Exception e) {
            log.error("[ImageRecognition] 图像超分失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 图片识别模型枚举（按能力分组）。
     *
     * @return 能力分组 → 模型 ID 列表
     */
    @Permission("admin")
    @Operation(summary = "图片识别模型清单", description = "枚举可用的图像分类/目标检测/描述/超分模型")
    @GetMapping("/models")
    public ReturnResult<?> models() {
        try {
            return ReturnResult.ok(recognitionClientManager.listModels());
        } catch (Exception e) {
            log.error("[ImageRecognition] 模型清单枚举失败: {}", e.getMessage());
            return ReturnResult.error("模型清单枚举失败: " + e.getMessage());
        }
    }
}
