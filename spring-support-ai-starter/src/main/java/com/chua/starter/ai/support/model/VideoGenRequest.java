package com.chua.starter.ai.support.model;

import lombok.Data;

/**
 * 文生视频请求参数
 *
 * @author CH
 */
@Data
public class VideoGenRequest {
    /** 提示词 */
    /** Prompt */
    private String prompt;
    /** 模型名称，可选 */
    /** 模型 */
    private String model;
    /** 时长（秒），可选 */
    /** 持续时间 */
    private Integer duration;
    /** 尺寸，可选 */
    /** 尺寸 */
    private String size;
    /** 风格，可选 */
    /** Style */
    private String style;
}
