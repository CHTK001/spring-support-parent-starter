package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.ocr.OcrResult;
import com.chua.starter.ai.support.configuration.ocr.OcrClientManager;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI OCR 文字识别控制器。
 *
 * <p>提供图片文字识别执行能力（检测 → 裁剪 → 方向矫正 → 修复 → 识别），
 * 全部配置读取自 {@code ocr} 分组全局设置，配置变更实时生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/ocr")
@RequiredArgsConstructor
@Tag(name = "AI OCR 文字识别", description = "图片文字识别、检测标注、模型清单")
public class OcrController {

    /**
     * OCR 客户端管理器
     */
    private final OcrClientManager ocrClientManager;

    /**
     * 图片文字识别（检测框 + 文本 + 置信度）。
     *
     * @param file 图片文件
     * @return 识别结果（文本 + 明细 + 数量 + 耗时）
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Permission("admin")
    @Operation(summary = "OCR 文字识别", description = "识别图片中全部文字块，返回拼接文本、明细（文本/置信度/位置/角度）与耗时")
    @PostMapping("/recognize")
    public ReturnResult<?> recognize(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            long start = System.currentTimeMillis();
            List<OcrResult> results = ocrClientManager.recognizeDetail(file.getBytes());
            String text = results.stream()
                    .map(OcrResult::text)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining("\n"));
            return ReturnResult.ok(Map.of(
                    "count", results.size(),
                    "text", text,
                    "results", results,
                    "elapsedMs", System.currentTimeMillis() - start
            ));
        } catch (Throwable e) {
            // 这里必须是 Throwable，不能是 Exception：模型文件缺失时底层抛的是
            // UnsatisfiedLinkError（Error 的子类），catch (Exception) 接不住，它会一路
            // 逃到全局异常处理器，被整成一句「系统繁忙，请稍后重试」，把
            // 「未在 classpath 找到匹配 *.onnx 的文件: ocr/PP-OCRv6/tiny/det_infer/」
            // 这条唯一能定位问题的信息吞得一干二净（实测：前端只显示系统繁忙，
            // 必须翻服务器日志才知道是模型没放）。
            log.error("[Ocr] 文字识别失败", e);
            return ReturnResult.error("文字识别失败: " + e.getMessage());
        }
    }

    /**
     * OCR 检测标注图（旋转框 + 识别文字）。
     *
     * @param file 图片文件
     * @return 标注后 JPEG 图片
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Permission("admin")
    @Operation(summary = "OCR 检测标注图", description = "返回标注了检测框与识别文字的 JPEG 图片")
    @PostMapping(value = "/annotate", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> annotate(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            byte[] result = ocrClientManager.annotate(file.getBytes());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(result);
        } catch (Throwable e) {
            // 同上：Error 也要接住，否则真正的失败原因进不了日志与响应
            log.error("[Ocr] 检测标注失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * OCR 模型枚举（按能力分组）。
     *
     * @return 能力分组 → 模型 标识 列表
     */
    @Permission("admin")
    @Operation(summary = "OCR 模型清单", description = "枚举可用的文字检测/识别/方向矫正/修复模型")
    @GetMapping("/models")
    public ReturnResult<?> models() {
        try {
            return ReturnResult.ok(ocrClientManager.listModels());
        } catch (Throwable e) {
            log.error("[Ocr] 模型清单枚举失败: {}", e.getMessage(), e);
            return ReturnResult.error("模型清单枚举失败: " + e.getMessage());
        }
    }
}
