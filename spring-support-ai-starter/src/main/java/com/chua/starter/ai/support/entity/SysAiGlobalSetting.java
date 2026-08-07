package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 全局设置实体，对应表 {@code sys_ai_global_setting}。
 *
 * <p>以「分组 + 字段」键值行保存全局配置（如 global 分组的 default_model、rag_splitter 等）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_global_setting")
public class SysAiGlobalSetting {

    /**
     * 全局设置主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long sysAiGlobalSettingId;

    /**
     * 设置分组（如 global / runtime / rag）
     */
    @TableField("sys_ai_global_setting_group")
    private String sysAiGlobalSettingGroup;

    /**
     * 字段名
     */
    @TableField("sys_ai_global_setting_field")
    private String sysAiGlobalSettingField;

    /**
     * 字段值
     */
    @TableField("sys_ai_global_setting_value")
    private String sysAiGlobalSettingValue;

    /**
     * 值类型（STRING / NUMBER / BOOLEAN）
     */
    @TableField("sys_ai_global_setting_value_type")
    private String sysAiGlobalSettingValueType;

    /**
     * 字段描述
     */
    @TableField("sys_ai_global_setting_description")
    private String sysAiGlobalSettingDescription;

    /**
     * 排序值
     */
    @TableField("sys_ai_global_setting_sort")
    private Integer sysAiGlobalSettingSort;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_global_setting_status")
    private Integer sysAiGlobalSettingStatus;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

