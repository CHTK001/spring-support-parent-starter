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
    private String prompt;
    /** 模型名称，可选 */
    private String model;
    /** 尺寸，如 1024x1024，可选 */
    private String size;
    /** 风格，可选 */
    private String style;
    /** 质量，可选 */
    private String quality;
}
