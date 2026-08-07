package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 用量记录表
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_usage")
public class SysAiUsage {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分组：chat/image/video/colorize/vision/rag
     */
    private String groupName;

    /**
     * 服务商：openai/zhipu/baidu 等
     */
    private String provider;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 调用类型：sync/async
     */
    private String callType;

    /**
     * 输入 Token 数
     */
    private Integer promptTokens;

    /**
     * 输出 Token 数
     */
    private Integer completionTokens;

    /**
     * 总 Token 数
     */
    private Integer totalTokens;

    /**
     * 输入费用（单位：元）
     */
    private Double inputCost;

    /**
     * 输出费用（单位：元）
     */
    private Double outputCost;

    /**
     * 总费用（单位：元）
     */
    private Double totalCost;

    /**
     * 请求 ID（关联调用日志）
     */
    private String requestId;

    /**
     * 用户标识（可选）
     */
    private String userId;

    /**
     * 调用时间
     */
    private LocalDateTime callTime;

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

