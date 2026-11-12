package com.chua.starter.ai.support.paper.pipeline;

import com.chua.starter.ai.support.configuration.paper.PaperImageCorrector;
import com.chua.starter.ai.support.configuration.paper.PaperImagePreprocessor;
import com.chua.starter.ai.support.paper.pipeline.config.PaperPipelineProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Paper 图片扫描管线。
 *
 * <p>把散落在阅卷服务里的图片处理步骤收敛为一条管线：
 * <ol>
 *   <li>ONNX 卡片/布局矫正（可选，默认关闭）</li>
 *   <li>既有照度校正/尺度归一化预处理（可配置关闭）</li>
 *   <li>版面分析 + OCR + 结构化扫描</li>
 * </ol>
 * 未开启布局矫正时与旧实现完全一致，只执行原有预处理后送入 {@link EduPaperScanner}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperScanPipeline {

    /**
     * 布局矫正器
     */
    private final PaperImageCorrector layoutCorrector;

    /**
     * 版面 + OCR 扫描器
     */
    private final EduPaperScanner paperScanner;

    /**
     * 教育 AI 配置
     */
    private final PaperPipelineProperties properties;

    /**
     * 管线执行结果。
     *
     * @param scan      既有扫描结果
     * @param corrected 本次是否应用了布局矫正
     */
    public record ScanOutcome(EduPaperScanner.ScanResult scan, boolean corrected) {
    }

    /**
     * 执行图片级扫描管线。
     *
     * @param imageData 原始图片字节
     * @return 扫描结果与布局矫正标记
     */
    public ScanOutcome scan(byte[] imageData) {
        boolean corrected = false;
        byte[] input = imageData;
        if (properties.getMarking().isLayoutCorrection()) {
            byte[] correctedData = layoutCorrector.correct(input);
            corrected = correctedData != input;
            input = correctedData;
        }
        byte[] prepared = properties.getMarking().isPreprocess()
                ? PaperImagePreprocessor.preprocess(input) : input;
        return new ScanOutcome(paperScanner.scan(prepared), corrected);
    }
}