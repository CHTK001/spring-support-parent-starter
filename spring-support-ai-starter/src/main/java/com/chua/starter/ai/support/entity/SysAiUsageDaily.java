package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 用量日汇总表
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_usage_daily")
public class SysAiUsageDaily {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 分组
     */
    private String groupName;

    /**
     * 服务商
     */
    private String provider;

    /**
     * 模型
     */
    private String model;

    /**
     * 总调用次数
     */
    private Integer totalCalls;

    /**
     * 总输入 Token
     */
    private Integer totalPromptTokens;

    /**
     * 总输出 Token
     */
    private Integer totalCompletionTokens;

    /**
     * 总 Token
     */
    private Integer totalTokens;

    /**
     * 总费用（元）
     */
    private Double totalCost;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
