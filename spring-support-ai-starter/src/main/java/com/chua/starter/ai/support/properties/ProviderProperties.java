package com.chua.starter.ai.support.properties;

import lombok.Data;

/**
 * 云厂商配置属性
 *
 * @author CH
 * @since 2025/01/XX
 */
@Data
public class ProviderProperties {
    /**
     * 应用ID（腾讯云、讯飞等）
     */
    private String appId;

    /**
     * 应用密钥（阿里云、讯飞等）
     */
    private String appKey;

    /**
     * 应用密钥（腾讯云、讯飞等）
     */
    private String appSecret;

    /**
     * API密钥（OpenAI、百度等）
     */
    private String apiKey;

    /**
     * 密钥（百度、腾讯云等）
     */
    private String secretKey;

    /**
     * API地址
     */
    private String apiAddress;

    /**
     * 基础URL
     */
    private String baseUrl;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 区域
     */
    private String region;

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout;

    /**
     * 本地模型路径
     */
    private String modelPath;

    /**
     * 设备（cpu, cuda, mps）
     */
    private String device;

    /**
     * 线程数
     */
    private Integer threads;
}
