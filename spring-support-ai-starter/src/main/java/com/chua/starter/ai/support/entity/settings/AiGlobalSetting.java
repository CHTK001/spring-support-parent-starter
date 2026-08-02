package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 全局设置表，单条记录（id=1），保存应用级默认参数。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@TableName("ai_global_setting")
@ApiModel("全局设置")
public class AiGlobalSetting implements Serializable {

    /**
     * 设置主键 ID
     */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty("设置主键 ID")
    private Long settingId;

    /**
     * 是否启用 ChatClient 聚合
     */
    @TableField("setting_chat_client_enabled")
    @ApiModelProperty("是否启用 ChatClient 聚合")
    private Boolean settingChatClientEnabled;

    /**
     * 用量自动刷新间隔（秒）
     */
    @TableField("setting_usage_interval_sec")
    @ApiModelProperty("用量自动刷新间隔(秒)")
    private Integer settingUsageIntervalSec;

    /**
     * 是否启用用量分析
     */
    @TableField("setting_enable_usage_analysis")
    @ApiModelProperty("是否启用用量分析")
    private Boolean settingEnableUsageAnalysis;

    /**
     * 默认模型
     */
    @TableField("setting_default_model")
    @ApiModelProperty("默认模型")
    private String settingDefaultModel;

    /**
     * 默认温度
     */
    @TableField("setting_temperature")
    @ApiModelProperty("默认温度")
    private BigDecimal settingTemperature;

    /**
     * 默认最大 tokens
     */
    @TableField("setting_max_tokens")
    @ApiModelProperty("默认最大 tokens")
    private Integer settingMaxTokens;

    /**
     * 默认请求超时（秒）
     */
    @TableField("setting_timeout")
    @ApiModelProperty("默认请求超时(秒)")
    private Integer settingTimeout;

    /**
     * 是否启用缓存
     */
    @TableField("setting_enable_cache")
    @ApiModelProperty("是否启用缓存")
    private Boolean settingEnableCache;

    /**
     * 是否流式输出
     */
    @TableField("setting_stream")
    @ApiModelProperty("是否流式输出")
    private Boolean settingStream;

    /**
     * 更新时间
     */
    @TableField(value = "setting_update_time", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty("更新时间")
    private LocalDateTime settingUpdateTime;
}