package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 版面分析结果
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutAnalysisResult implements Serializable {

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
     * 版面元素列表
     */
    private List<LayoutElement> elements;

    /**
     * 处理耗时（毫秒）
     */
    private long costTime;

    /**
     * 版面元素类型
     */
    public enum ElementType {
        /**
         * 文本
         */
        TEXT,
        /**
         * 标题
         */
        TITLE,
        /**
         * 图片
         */
        IMAGE,
        /**
         * 表格
         */
        TABLE,
        /**
         * 列表
         */
        LIST,
        /**
         * 图表
         */
        FIGURE,
        /**
         * 公式
         */
        FORMULA,
        /**
         * 页眉
         */
        HEADER,
        /**
         * 页脚
         */
        FOOTER,
        /**
         * 其他
         */
        OTHER
    }

    /**
     * 版面元素信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayoutElement implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 元素类型
         */
        private ElementType type;

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
         * 元素内容（如有）
         */
        private String content;

        /**
         * 元素顺序
         */
        private int order;
    }

    /**
     * 创建成功结果
     */
    public static LayoutAnalysisResult success(List<LayoutElement> elements, long costTime) {
        return LayoutAnalysisResult.builder()
                .success(true)
                .elements(elements)
                .costTime(costTime)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static LayoutAnalysisResult fail(String errorMessage) {
        return LayoutAnalysisResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
