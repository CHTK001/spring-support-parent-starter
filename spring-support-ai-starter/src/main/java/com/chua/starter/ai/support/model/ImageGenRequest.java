package com.chua.starter.ai.support.model;

import lombok.Data;

/**
 * 文生图请求参数
 *
 * @author CH
 */
@Data
public class ImageGenRequest {
    /** 提示词 */
    /** Prompt */
    private String prompt;
    /** 模型名称，可选 */
    /** 模型 */
    private String model;
    /** 尺寸，如 1024x1024，可选 */
    /** 尺寸 */
    private String size;
    /** 风格，可选 */
    /** Style */
    private String style;
    /** 质量，可选 */
    /** Quality */
    private String quality;
}
