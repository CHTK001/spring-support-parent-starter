package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 性别年龄检测结果
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenderAgeResult implements Serializable {

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
     * 检测结果列表
     */
    private List<GenderAge> results;

    /**
     * 处理耗时（毫秒）
     */
    private long costTime;

    /**
     * 性别枚举
     */
    public enum Gender {
        /**
         * 男性
         */
        MALE,
        /**
         * 女性
         */
        FEMALE,
        /**
         * 未知
         */
        UNKNOWN
    }

    /**
     * 性别年龄信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenderAge implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 性别
         */
        private Gender gender;

        /**
         * 性别置信度
         */
        private float genderConfidence;

        /**
         * 年龄
         */
        private int age;

        /**
         * 年龄置信度
         */
        private float ageConfidence;

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
    }

    /**
     * 创建成功结果
     */
    public static GenderAgeResult success(List<GenderAge> results, long costTime) {
        return GenderAgeResult.builder()
                .success(true)
                .results(results)
                .costTime(costTime)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static GenderAgeResult fail(String errorMessage) {
        return GenderAgeResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
