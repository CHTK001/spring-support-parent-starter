package com.chua.starter.project.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 图片请求参数类
 *
 * @author CH
 * @since 2025/9/8 21:46
 */
@Data
@Schema(description = "图片请求")
public class ImageRequest {

    /**
     * 请求ID，用于标识一次图片处理请求
     * 示例：request-20250908-001
     */
    @Schema(description = "请求id")
    private String requestId;

    /**
     * 模型名称，指定使用的图片处理模型
     * 示例：image-classification-v1
     */
    @Schema(description = "模型")
    private String model;

    /**
     * 图片处理服务提供商，指定图片处理服务提供商
     * 示例：aliyun
     */
    @Schema(description = "处理器提供商")
    private String provider;
}
