package com.chua.starter.ai.support.configuration.ocr;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.deeplearning.support.ocr.OcrResult;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.client.ModelIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * OCR 识别客户端管理器。
 *
 * <p>按 {@code ocr} 分组全局设置（sys_ai_global_setting）懒加载
 * {@link OcrPipeline}（检测 → 裁剪 → 方向矫正 → 修复 → 识别 → 文本矫正），配置通过
 * {@link OcrSettingsChangedEvent} 热重建，实时生效。模型懒加载：
 * 仅在首次调用对应能力时由引擎创建模型实例，避免启动阻塞。文本矫正
 * （corrector_model，如 macbert4csc）非必填：留空不启用，矫正失败保留原文。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrClientManager {

    /**
     * 全局设置键值分组
     */
    private static final String GROUP = "ocr";

    /**
     * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

    /**
     * 当前生效的 OCR 管线（懒构建，热更新时替换）
     */
    private volatile OcrPipeline pipeline;

    /**
     * 获取当前 OCR 管线（懒加载）。
     *
     * @return OCR 管线
     * @throws IllegalStateException 检测/识别模型未配置时
     */
    public OcrPipeline current() {
        OcrPipeline current = pipeline;
        if (current == null) {
            synchronized (this) {
                if (pipeline == null) {
                    pipeline = build();
                }
                current = pipeline;
            }
        }
        return current;
    }

    /**
     * 整图识别（拼接全部文本块）。
     *
     * @param imageData 图片字节
     * @return 识别文本
     */
    public String recognize(byte[] imageData) {
        OcrImagePreprocessor.Result pre = preprocess(imageData);
        return current().recognize(pre.data());
    }

    /**
     * 详细识别（文本块 + 置信度 + 位置）。
     *
     * <p>前置增强（缩放/纠偏/对比度/降噪/二值化）都是<b>可选</b>项，未开启时原图直通；
     * 开启后坐标会从「处理后空间」逆映射回原图，保证调用方拿到的仍是原图坐标。</p>
     *
     * @param imageData 图片字节
     * @return 识别结果列表（坐标已映射回原图，且按 ocr_confidence_threshold 过滤）
     */
    public List<com.chua.deeplearning.support.ocr.OcrResult> recognizeDetail(byte[] imageData) {
        OcrImagePreprocessor.Result pre = preprocess(imageData);
        List<OcrResult> raw = current().recognizeDetail(pre.data());
        return restore(raw, pre);
    }

    /**
     * 读取前置增强参数（全部可选，缺省不改变行为）。
     *
     * @return 参数
     */
    private OcrImagePreprocessor.Options preprocessOptions() {
        return new OcrImagePreprocessor.Options(
                settingReader.getInt(GROUP, "ocr_max_image_size", 0),
                settingReader.getBool(GROUP, "enhance_deskew", false),
                settingReader.getBool(GROUP, "enhance_contrast", false),
                settingReader.getBool(GROUP, "enhance_denoise", false),
                settingReader.getBool(GROUP, "enhance_binarize", false));
    }

    /**
     * 需要时执行前置增强。
     *
     * @param imageData 原图
     * @return 增强结果（未开启时原图直通）
     */
    private OcrImagePreprocessor.Result preprocess(byte[] imageData) {
        OcrImagePreprocessor.Options options = preprocessOptions();
        if (options.noop()) {
            return OcrImagePreprocessor.passthroughOf(imageData);
        }
        return OcrImagePreprocessor.process(imageData, options);
    }

    /**
     * 坐标回映射 + 置信度过滤。
     *
     * @param results 处理后空间的识别结果
     * @param pre     预处理结果
     * @return 原图坐标结果
     */
    private List<OcrResult> restore(List<OcrResult> results, OcrImagePreprocessor.Result pre) {
        if (results == null || results.isEmpty()) {
            return results == null ? List.of() : results;
        }
        double minConfidence = settingReader.getInt(GROUP, "ocr_confidence_threshold", 0);
        // 语义：0~1 的比例阈值；0（或未配置）表示不过滤。
        // 兼容历史库里的百分数写法（>1 视为百分数）。
        if (minConfidence > 1.0d) {
            minConfidence = minConfidence / 100.0d;
        }
        List<OcrResult> out = new java.util.ArrayList<>(results.size());
        for (OcrResult item : results) {
            if (item == null) {
                continue;
            }
            // 置信度阈值可选：0（或未配置）表示不过滤；否则丢弃低于阈值的块
            if (minConfidence > 0 && item.confidence() > 0 && item.confidence() < minConfidence) {
                continue;
            }
            com.chua.deeplearning.support.model.PredictRectangle box = item.boundingBox();
            if (box == null || !pre.transformed()) {
                out.add(item);
                continue;
            }
            double[] p1 = pre.mapBack(box.x(), box.y());
            double[] p2 = pre.mapBack(box.x() + box.width(), box.y() + box.height());
            double x = Math.min(p1[0], p2[0]);
            double y = Math.min(p1[1], p2[1]);
            com.chua.deeplearning.support.model.PredictRectangle moved =
                    new com.chua.deeplearning.support.model.PredictRectangle(
                            (float) x, (float) y,
                            (float) Math.abs(p2[0] - p1[0]), (float) Math.abs(p2[1] - p1[1]),
                            box.confidence(), box.label(), box.labelName(), box.keypoints());
            out.add(new OcrResult(item.text(), item.confidence(), moved, item.angle()));
        }
        return out;
    }

    /**
     * OCR 是否已配置可用（detector_model / recognizer_model 至少其一非空）。
     * <p>供版面联动等上层逻辑判断能否降级，避免直接触发未配置异常。</p>
     *
     * @return true 表示 OCR 可用
     */
    public boolean available() {
        String detector = settingReader.getStr(GROUP, "detector_model");
        String recognizer = settingReader.getStr(GROUP, "recognizer_model");
        return StringUtils.isNotBlank(detector) || StringUtils.isNotBlank(recognizer);
    }

    /**
     * 在给定区域集合内识别（供版面联动：先版面分析裁剪，再逐区域识别）。
     *
     * @param imageData 图片字节
     * @param regions  需要识别的区域（像素坐标）
     * @return 识别结果列表（位置已回填为原图坐标）
     */
    public List<OcrResult> recognizeRegions(byte[] imageData,
                                           List<com.chua.deeplearning.support.model.PredictRectangle> regions) {
        if (regions == null || regions.isEmpty()) {
            return List.of();
        }
        OcrImagePreprocessor.Result pre = preprocess(imageData);
        byte[] work = pre.data();
        OcrPipeline ocr = current();
        List<OcrResult> results = new java.util.ArrayList<>();
        for (com.chua.deeplearning.support.model.PredictRectangle region : regions) {
            // 区域来自原图（版面分析结果）；若做过缩放/纠偏，先映射到处理后的坐标系再裁剪
            com.chua.deeplearning.support.model.PredictRectangle target = region;
            if (pre.transformed()) {
                double[] p1 = pre.mapForward(region.x(), region.y());
                double[] p2 = pre.mapForward(region.x() + region.width(), region.y() + region.height());
                double x = Math.min(p1[0], p2[0]);
                double y = Math.min(p1[1], p2[1]);
                target = new com.chua.deeplearning.support.model.PredictRectangle(
                        (float) x, (float) y,
                        (float) Math.abs(p2[0] - p1[0]), (float) Math.abs(p2[1] - p1[1]),
                        region.confidence(), region.label(), region.labelName(), region.keypoints());
            }
            byte[] crop = com.chua.deeplearning.support.utils.ImageCropUtils.crop(work, target);
            if (crop == null || crop.length == 0) {
                continue;
            }
            for (OcrResult item : ocr.recognizeDetail(crop)) {
                results.add(offset(item, target));
            }
        }
        return restore(results, pre);
    }

    /**
     * 将区域内的识别结果坐标回填为原图坐标。
     *
     * @param item   区域内识别结果
     * @param region 区域
     * @return 坐标已平移的结果
     */
    private static OcrResult offset(OcrResult item, com.chua.deeplearning.support.model.PredictRectangle region) {
        com.chua.deeplearning.support.model.PredictRectangle box = item.boundingBox();
        if (box == null) {
            return item;
        }
        com.chua.deeplearning.support.model.PredictRectangle moved =
                new com.chua.deeplearning.support.model.PredictRectangle(
                        box.x() + region.x(),
                        box.y() + region.y(),
                        box.width(),
                        box.height(),
                        box.confidence(),
                        box.label(),
                        box.labelName(),
                        box.keypoints());
        return new OcrResult(item.text(), item.confidence(), moved, item.angle());
    }

    /**
     * 绘制检测标注图（框 + 识别文字）。
     *
     * <p>优先交给 {@link OcrPipeline#toDrawer(byte[])}；该实现内部会按区域的
     * 「单识别模型」语义调用 {@code recognizeDetail}，遇到「检测+识别」这类组合
     * 管线会抛 {@code IllegalStateException}（实测 paddleocrv6-det + -rec：
     * 「模型输出类型为 String，不是文字结果列表」）。标注图属于展示辅助，
     * 不该因此让接口 500，故失败时回退到本类自绘：直接用整图
     * {@code recognizeDetail} 的检测框与文本画框，输出同样的 JPEG。</p>
     *
     * @param imageData 图片字节
     * @return 标注后 JPEG 字节
     */
    public byte[] annotate(byte[] imageData) {
        // 与识别口径一致：标注也画在「增强后」的图上，否则框会和图错位
        OcrImagePreprocessor.Result pre = preprocess(imageData);
        byte[] work = pre.data();
        try {
            byte[] drawn = current().toDrawer(work);
            if (drawn != null && drawn.length > 0) {
                return drawn;
            }
            log.warn("[Ocr] 管线标注返回空，回退到自绘标注");
        } catch (Throwable t) {
            // 库里对组合管线的已知限制，不是配置错误：降级而不是失败
            log.warn("[Ocr] 管线标注不可用，回退到自绘标注: {}", t.getMessage());
        }
        return drawAnnotate(work);
    }

    /**
     * 自绘标注图：在识别结果的每个文本框上画框并写文字。
     *
     * @param imageData 原图字节
     * @return 标注后 JPEG 字节
     */
    private byte[] drawAnnotate(byte[] imageData) {
        List<OcrResult> results = current().recognizeDetail(imageData);
        try (java.io.InputStream in = new java.io.ByteArrayInputStream(imageData)) {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(in);
            if (src == null) {
                throw new IllegalStateException("图片解码失败，无法生成标注图");
            }
            java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(
                    src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = canvas.createGraphics();
            try {
                g.drawImage(src, 0, 0, null);
                g.setColor(java.awt.Color.RED);
                g.setStroke(new java.awt.BasicStroke(2f));
                int fontSize = Math.max(12, Math.min(28, src.getHeight() / 30));
                java.awt.Font font = new java.awt.Font(java.awt.Font.SANS_SERIF,
                        java.awt.Font.PLAIN, fontSize);
                g.setFont(font);
                for (OcrResult item : results) {
                    com.chua.deeplearning.support.model.PredictRectangle box = item.boundingBox();
                    if (box == null) {
                        continue;
                    }
                    int x = (int) box.x();
                    int y = (int) box.y();
                    int w = (int) box.width();
                    int h = (int) box.height();
                    g.drawRect(x, y, Math.max(1, w), Math.max(1, h));
                    String text = item.text();
                    if (StringUtils.isNotBlank(text)) {
                        g.drawString(text, x, Math.max(fontSize, y - 4));
                    }
                }
            } finally {
                g.dispose();
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(canvas, "jpg", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("标注图生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 枚举可用 OCR 模型清单（按能力分组）。
     *
     * @return 能力分组 → 模型 标识 列表
     */
    public Map<String, List<String>> listModels() {
        OcrPipeline probe = new OcrPipeline(
                com.chua.deeplearning.support.image.ImageDetector.create("none"),
                com.chua.deeplearning.support.ocr.OcrRecognizer.create("none"),
                null, null, false, true, 0f, 2, 40, 25f, false, 100f, false, false);
        return probe.listModels();
    }

    /**
     * 全局配置变更监听：ocr 分组更新后热重建，实时生效。
     *
     * @param event 配置变更事件
     */
    @EventListener
    public void onOcrSettingsChanged(OcrSettingsChangedEvent event) {
        if (event == null || !GROUP.equals(event.group())) {
            return;
        }
        if (event.userScoped()) {
            // OCR 管线是**全局共享**实例，只承载全局配置。按触发者的身份重建会把
            // 他的模型烘进去、串掉其他用户；按用户的 OCR 配置暂不支持（需按用户实例化）。
            log.debug("[OcrClientManager] 收到用户级 ocr 配置写入，共享 OCR 管线不重建");
            return;
        }
        log.info("[OcrClientManager] 收到 ocr 分组配置变更，开始热重建");
        /**
         * rebuild。
         */
        rebuild();
    }

    /**
     * 配置热更新：按最新配置重建 OCR 管线。
     */
    public synchronized void rebuild() {
        this.pipeline = build();
        log.info("[OcrClientManager] OCR 管线热更新完成");
    }

    /**
     * 按 ocr 分组配置构建管线。
     *
     * @return OCR 管线
     */
    private OcrPipeline build() {
        // 设置页模型选择器存「onnx:裸名」，注册表只认裸名——统一归一后再下传
        String detector = ModelIds.bare(settingReader.getStr(GROUP, "detector_model"));
        String recognizer = ModelIds.bare(settingReader.getStr(GROUP, "recognizer_model"));
        if (StringUtils.isBlank(detector) && StringUtils.isBlank(recognizer)) {
            /**
             * illegal状态异常。
             * @param recognizer_model" recognizer_模型"
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("OCR 模型未配置：请在设置中配置 ocr 分组的 detector_model / recognizer_model");
        }
        OcrPipeline.Builder builder = OcrPipeline.builder();
        if (StringUtils.isNotBlank(detector)) {
            builder.detector(detector);
        }
        if (StringUtils.isNotBlank(recognizer)) {
            builder.recognizer(recognizer);
        }
        var detectorThreshold = settingReader.getDecimalOrNull(GROUP, "detector_threshold");
        builder.minConfidence(detectorThreshold == null ? 0f : detectorThreshold.floatValue());
        builder.sortReadingOrder(settingReader.getBool(GROUP, "sort_reading_order", true));
        String direction = ModelIds.bare(settingReader.getStr(GROUP, "direction_model"));
        if (StringUtils.isNotBlank(direction)) {
            builder.direction(direction);
        }
        String restorer = ModelIds.bare(settingReader.getStr(GROUP, "restorer_model"));
        if (StringUtils.isNotBlank(restorer)) {
            builder.enhancer(restorer);
            builder.enhanceInPipeline(true);
        }
        // 文本矫正（macbert4csc 等）：非必填，留空关闭；矫正失败时 OcrPipeline 保留原文不阻断
        String corrector = ModelIds.bare(settingReader.getStr(GROUP, "corrector_model"));
        if (StringUtils.isNotBlank(corrector)) {
            builder.corrector(corrector);
        }
        // 桥接为模型级参数：device 与置信度阈值对 det / rec / direction / restorer / corrector
        // 全部生效，使 OCR 各模型可分别配置设备（原先 device 只被读取却从未使用）
        String device = settingReader.getStr(GROUP, "device");
        Float thresholdValue = detectorThreshold == null ? null : detectorThreshold.floatValue();
        for (String modelId : new String[]{detector, recognizer, direction, restorer, corrector}) {
            com.chua.starter.ai.support.configuration.client.ModelParamBridge
                    .bridge(modelId, device, thresholdValue);
        }
        return builder.build();
    }
}

