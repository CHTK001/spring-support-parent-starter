package com.chua.starter.spider.support.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务级 AI 大脑配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpiderAiProfile {

    /** AI 提供商（如 openai、zhipu、deepseek） */
    private String provider;

    /** 模型名称 */
    private String model;

    /** 温度参数（0.0 ~ 2.0） */
    private Double temperature;

    /** 上下文窗口大小（token 数） */
    private Integer contextWindow;

    /** 是否启用 AI 能力 */
    private Boolean enabled;
}
