package com.chua.starter.ai.support.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 检测配置
 * <p>
 * 用于配置各种检测任务的参数
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectionConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 置信度阈值（0-1之间）
     * 低于此阈值的结果将被过滤
     */
    @Builder.Default
    private Float confidenceThreshold = 0.5f;

    /**
     * 最小人脸尺寸（像素）
     * 小于此尺寸的人脸将被忽略
     */
    @Builder.Default
    private Integer minFaceSize = 20;

    /**
     * 最大人脸数量
     * 超过此数量的人脸将被忽略
     */
    @Builder.Default
    private Integer maxFaceCount = 10;

    /**
     * 是否返回人脸关键点
     */
    @Builder.Default
    private Boolean returnLandmarks = false;

    /**
     * 是否返回人脸属性（年龄、性别等）
     */
    @Builder.Default
    private Boolean returnAttributes = false;

    /**
     * 特征提取维度
     * 用于控制特征向量的维度
     */
    @Builder.Default
    private Integer featureDimension = 512;

    /**
     * 是否进行人脸对齐
     */
    @Builder.Default
    private Boolean alignFace = true;

    /**
     * 模型路径
     * 用于指定人脸识别模型文件的路径
     */
    private String modelPath;

    /**
     * 厂家/供应商
     * 用于标识使用的识别引擎厂家，如：baidu、tencent、aliyun等
     */
    private String vendor;

    /**
     * API密钥
     * 部分厂家需要API密钥进行认证
     */
    private String apiKey;

    /**
     * API密钥密钥
     * 部分厂家需要API密钥密钥进行认证
     */
    private String apiSecret;

    /**
     * 端点地址
     * 用于指定API服务的端点地址
     */
    private String endpoint;

    /**
     * 超时时间（毫秒）
     * 请求超时时间，默认30秒
     */
    @Builder.Default
    private Long timeout = 30000L;

    /**
     * 创建默认配置
     *
     * @return 默认配置实例
     */
    public static DetectionConfiguration defaultConfig() {
        return DetectionConfiguration.builder().build();
    }
}

