package com.chua.starter.ai.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 用量统计 DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@ApiModel("用量统计 DTO")
public class AiUsageDTO implements Serializable {

    /**
     * 模型名称
     */
    @ApiModelProperty("模型名称")
    private String model;

    /**
     * 请求次数
     */
    @ApiModelProperty("请求次数")
    private Long requests;

    /**
     * 提示 tokens
     */
    @ApiModelProperty("提示 tokens")
    private Long promptTokens;

    /**
     * 生成 tokens
     */
    @ApiModelProperty("生成 tokens")
    private Long completionTokens;

    /**
     * 总 tokens
     */
    @ApiModelProperty("总 tokens")
    private Long totalTokens;

    /**
     * 统计时间戳
     */
    @ApiModelProperty("统计时间戳")
    private String timestamp;
}
