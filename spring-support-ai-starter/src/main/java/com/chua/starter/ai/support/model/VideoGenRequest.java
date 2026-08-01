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
    private String prompt;
    /** 模型名称，可选 */
    private String model;
    /** 时长（秒），可选 */
    private Integer duration;
    /** 尺寸，可选 */
    private String size;
    /** 风格，可选 */
    private String style;
}
