package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * OCR识别结果
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 完整识别文本
     */
    private String fullText;

    /**
     * 文本行列表
     */
    private List<TextLine> textLines;

    /**
     * 处理耗时（毫秒）
     */
    private long costTime;

    /**
     * 文本行信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextLine implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 文本内容
         */
        private String text;

        /**
         * 置信度
         */
        private float confidence;

        /**
         * 边界框 - 左上角X坐标
         */
        private int x;

        /**
         * 边界框 - 左上角Y坐标
         */
        private int y;

        /**
         * 边界框 - 宽度
         */
        private int width;

        /**
         * 边界框 - 高度
         */
        private int height;

        /**
         * 多边形顶点坐标
         */
        private List<int[]> polygon;
    }

    /**
     * 创建成功结果
     */
    public static OcrResult success(String fullText, List<TextLine> textLines, long costTime) {
        return OcrResult.builder()
                .success(true)
                .fullText(fullText)
                .textLines(textLines)
                .costTime(costTime)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static OcrResult fail(String errorMessage) {
        return OcrResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
