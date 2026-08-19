package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 用量日汇总表
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_usage_daily")
public class SysAiUsageDaily {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    /** ID */
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
    /** Create时间 */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** Update时间 */
    private LocalDateTime updateTime;
}

