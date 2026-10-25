package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.configuration.paper.PaperClientManager;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 试卷识别控制器。
 *
 * <p>配置全部读取自 {@code paper} 分组全局设置（系统设置 → 试卷配置），变更实时生效：</p>
 * <ul>
 *   <li>{@code engine=ocr}：版面分析 + 文字识别，返回逐题文字块；</li>
 *   <li>{@code engine=ocr+llm}：OCR 后再调大模型结构化出「题号 / 题干 / 作答 / 分值」。</li>
 * </ul>
 *
 * <p>模型字段留空时按「本分组 → ocr / layout 分组」复用优先，因此只要 OCR 配置可用
 * 就能直接跑通纯 OCR 引擎。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/paper")
@RequiredArgsConstructor
@Tag(name = "AI 试卷识别", description = "试卷文字块识别与题目结构化")
public class PaperController {

    /**
     * 试卷识别管理器
     */
    private final PaperClientManager paperClientManager;

    /**
     * 试卷识别。
     *
     * <p>{@code engine=ocr} 返回 {@code blocks}（文字块 + 坐标）；
     * {@code engine=ocr+llm} 返回 {@code questions}（结构化题目）。</p>
     *
     * @param file 试卷图片
     * @return 识别结果
     */
    @Permission("admin")
    @Operation(summary = "试卷识别", description = "按 paper 分组的 engine 执行：纯 OCR 出文字块，OCR+LLM 出结构化题目")
    @PostMapping("/recognize")
    public ReturnResult<?> recognize(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        if (!paperClientManager.available()) {
            return ReturnResult.error("试卷识别不可用：请先在「系统设置 → 试卷配置」或「OCR 配置」中配置文字检测/识别模型");
        }
        try {
            long start = System.currentTimeMillis();
            Map<String, Object> result = new LinkedHashMap<>(paperClientManager.recognize(file.getBytes()));
            result.put("elapsedMs", System.currentTimeMillis() - start);
            return ReturnResult.ok(result);
        } catch (Throwable e) {
            // 与 OcrController 同口径：模型缺失时底层抛的是 Error（UnsatisfiedLinkError），
            // catch(Exception) 接不住，会把真正的失败原因吞成「系统繁忙」
            log.error("[Paper] 试卷识别失败", e);
            return ReturnResult.error("试卷识别失败: " + e.getMessage());
        }
    }

    /**
     * 试卷识别配置状态。
     *
     * @return 引擎与可用性
     */
    @Permission("admin")
    @Operation(summary = "试卷识别状态", description = "返回当前引擎（ocr / ocr+llm）与是否可用")
    @GetMapping("/status")
    public ReturnResult<?> status() {
        Map<String, Object> out = new LinkedHashMap<>(3);
        out.put("engine", paperClientManager.engine());
        out.put("available", paperClientManager.available());
        return ReturnResult.ok(out);
    }
}
