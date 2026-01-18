package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 人脸检测结果
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceDetectionResult implements Serializable {

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
     * 检测到的人脸列表
     */
    private List<Face> faces;

    /**
     * 处理耗时（毫秒）
     */
    private long costTime;

    /**
     * 人脸信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Face implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 人脸边界框 - 左上角X坐标
         */
        private int x;

        /**
         * 人脸边界框 - 左上角Y坐标
         */
        private int y;

        /**
         * 人脸边界框 - 宽度
         */
        private int width;

        /**
         * 人脸边界框 - 高度
         */
        private int height;

        /**
         * 置信度
         */
        private float confidence;

        /**
         * 人脸关键点
         */
        private List<float[]> landmarks;
    }

    /**
     * 创建成功结果
     */
    public static FaceDetectionResult success(List<Face> faces, long costTime) {
        return FaceDetectionResult.builder()
                .success(true)
                .faces(faces)
                .costTime(costTime)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static FaceDetectionResult fail(String errorMessage) {
        return FaceDetectionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
