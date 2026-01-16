package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 图片检测结果
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageDetectionResult implements Serializable {

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
     * 检测到的物体列表
     */
    private List<DetectedObject> objects;

    /**
     * 处理耗时（毫秒）
     */
    private long costTime;

    /**
     * 检测到的物体
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectedObject implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物体类别名称
         */
        private String className;

        /**
         * 物体类别ID
         */
        private int classId;

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
    }

    /**
     * 创建成功结果
     */
    public static ImageDetectionResult success(List<DetectedObject> objects, long costTime) {
        return ImageDetectionResult.builder()
                .success(true)
                .objects(objects)
                .costTime(costTime)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static ImageDetectionResult fail(String errorMessage) {
        return ImageDetectionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
