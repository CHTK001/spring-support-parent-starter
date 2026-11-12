package com.chua.starter.ai.support.paper.pipeline.strategy;

import com.chua.starter.ai.support.paper.pipeline.PaperScanner;

import java.util.List;

/**
 * 任意卷识别策略（SPI）。
 *
 * <p>把「一份外来卷怎么被识别成题目列表」这件事从 {@link PaperScanner} 里抽成可插拔扩展点，
 * 由系统配置 {@code plugin.eduai.marking.extract-strategy} 选择实现（默认 {@code whole}）。</p>
 *
 * <p>内置实现：</p>
 * <ul>
 *   <li>{@code whole} —— 整卷一次识别（默认，与历史行为一致，调用量最小）；</li>
 *   <li>{@code region} —— 按版面区域分块识别（区域切不出来时自动回退整卷）。</li>
 * </ul>
 *
 * <p>新增实现只要实现本接口并声明为 Spring Bean，key 即成为可选配置值，无需改调用方。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface AnyExtractStrategy {

    /**
     * 策略 key（对应配置值）。
     *
     * @return 策略标识，如 {@code whole} / {@code region}
     */
    String key();

    /**
     * 识别一份外来卷。
     *
     * @param context 识别上下文（文字块、全文、版面区域与回调能力）
     * @return 题目列表 + 卷面学生
     */
    PaperScanner.AnyScan extract(Context context);

    /**
     * 识别上下文：把扫描器已有的能力以回调形式交给策略，策略不必依赖扫描器本身
     * （避免「扫描器持有策略、策略又依赖扫描器」的循环依赖）。
     */
    interface Context {

        /**
         * 版面文字块（含坐标），可能为空。
         *
         * @return 文字块
         */
        List<PaperScanner.ScanBlock> blocks();

        /**
         * 版面全文。
         *
         * @return 全文文本
         */
        String text();

        /**
         * 版面区域（含坐标），可能为空。
         *
         * @return 版面区域
         */
        List<PaperScanner.LayoutBox> layoutBoxes();

        /**
         * 版面规则切题：按「题号块 → 后续块」切出题号与作答（不调模型）。
         *
         * @return 题目；切不出时为空
         */
        List<PaperScanner.ScannedQuestion> layoutQuestions();

        /**
         * 用模型给题目补题干（一次调用）。
         *
         * @param base 版面规则切出的题目
         * @return 补好题干的题目；失败或为空时返回空列表
         */
        List<PaperScanner.ScannedQuestion> fillStems(List<PaperScanner.ScannedQuestion> base);

        /**
         * 整卷模型抽取（一次调用）：题号/题干/作答/分值 + 卷面学生。
         *
         * @return 抽取结果；失败时抛异常由策略决定是否降级
         */
        PaperScanner.AnyScan modelExtract();

        /**
         * 行规则切题兜底（不调模型）。
         *
         * @return 题目（题干留空）
         */
        List<PaperScanner.ScannedQuestion> ruleQuestions();

        /**
         * 从卷头行正则抽「姓名 / 学号」。
         *
         * @return [姓名, 学号]，识别不到为 null
         */
        String[] studentByLines();

        /**
         * 对一段文本做模型抽取（区域策略按区域分块时用）。
         *
         * @param regionText 该区域的文本
         * @return 抽取结果；失败时抛异常
         */
        PaperScanner.AnyScan modelExtract(String regionText);
    }
}