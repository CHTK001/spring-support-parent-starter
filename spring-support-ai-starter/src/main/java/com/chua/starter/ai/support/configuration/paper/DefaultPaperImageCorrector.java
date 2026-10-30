package com.chua.starter.ai.support.configuration.paper;

import com.chua.deeplearning.support.onnx.classification.CardCorrectionTranslator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@link PaperImageCorrector} 的默认实现：复用 ONNX {@link CardCorrectionTranslator}
 * （card-correction-detector）做四边形检测与透视拉平。
 *
 * <p>模型未随包提供、检测不到卡片或执行失败时一律返回原图，保证该功能是可选项、
 * 失败也不阻断识别。</p>
 *
 * @since 4.0.0.44
 * @author CH
 */
@Slf4j
@Component
public class DefaultPaperImageCorrector implements PaperImageCorrector {

    /**
     * 把外部模型根目录下的矫正模型同步到矫正器缓存。
     *
     * <p>生产 jar 默认不打包 40MB 权重，远端通过
     * {@code -Ddeeplearning.model.root-dir} 指向 {@code models/onnx}；
     * 这里按注册器同款相对路径（{@code cv/card_correction/card_detection.onnx}）预置到
     * {@code java.io.tmpdir/chua-models/card-correction}，使 {@link CardCorrectionTranslator}
     * 无需依赖 classpath 内嵌模型。文件已存在且大小一致时不重复拷贝。</p>
     */
    private void seedExternalModel() {
        String root = System.getProperty("deeplearning.model.root-dir");
        if (root == null || root.isBlank()) {
            return;
        }
        Path external = Path.of(root, "cv", "card_correction", "card_detection.onnx");
        if (!Files.isRegularFile(external)) {
            log.debug("[PaperImage] 外部布局矫正模型不存在，跳过预置: {}", external);
            return;
        }
        try {
            Path modelDir = Path.of(System.getProperty("java.io.tmpdir"), "chua-models", "card-correction");
            Files.createDirectories(modelDir);
            Path target = modelDir.resolve("card_detection.onnx");
            if (!Files.isRegularFile(target) || Files.size(target) != Files.size(external)) {
                Files.copy(external, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("[PaperImage] 布局矫正模型已同步到缓存: {} -> {}", external, target);
            }
        } catch (Exception e) {
            log.warn("[PaperImage] 布局矫正模型预置失败，将由原实现尝试内嵌模型: {}", e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param imageData 原始图片字节
     * @return 矫正后的 PNG 字节；未检出卡片或执行失败时返回原图
     */
    @Override
    public byte[] correct(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return imageData;
        }
        try {
            seedExternalModel();
            byte[] corrected = CardCorrectionTranslator.shared().correct(imageData);
            if (corrected == null || corrected.length == 0) {
                log.info("[PaperImage] 布局矫正未检出卡片，使用原图（{} 字节）", imageData.length);
                return imageData;
            }
            log.info("[PaperImage] 布局矫正已应用：{} -> {} 字节", imageData.length, corrected.length);
            return corrected;
        } catch (Exception e) {
            log.warn("[PaperImage] 布局矫正失败，使用原图: {}", e.getMessage());
            return imageData;
        }
    }
}
