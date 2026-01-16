package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 特征值提取结果
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureResult implements Serializable {

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
     * 特征向量
     */
    private float[] features;

    /**
     * 特征维度
     */
    private int dimension;

    /**
     * 处理耗时（毫秒）
     */
    private long costTime;

    /**
     * 创建成功结果
     */
    public static FeatureResult success(float[] features, long costTime) {
        return FeatureResult.builder()
                .success(true)
                .features(features)
                .dimension(features != null ? features.length : 0)
                .costTime(costTime)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static FeatureResult fail(String errorMessage) {
        return FeatureResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 计算与另一个特征的余弦相似度
     *
     * @param other 另一个特征结果
     * @return 余弦相似度 (0-1)
     */
    public float cosineSimilarity(FeatureResult other) {
        if (!this.success || !other.success || this.features == null || other.features == null) {
            return 0f;
        }
        if (this.features.length != other.features.length) {
            return 0f;
        }

        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;

        for (int i = 0; i < this.features.length; i++) {
            dotProduct += this.features[i] * other.features[i];
            normA += this.features[i] * this.features[i];
            normB += other.features[i] * other.features[i];
        }

        if (normA == 0 || normB == 0) {
            return 0f;
        }

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
