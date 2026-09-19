package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 用户设置覆盖实体，对应表 {@code sys_ai_user_setting}。
 *
 * <p>与 {@link SysAiGlobalSetting} 同构的「分组 + 字段」键值行，多一列归属用户。
 * 全局默认行留在 {@code sys_ai_global_setting}，本表只存用户个人改过的项，
 * 未覆盖的字段自动回落到全局默认。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_user_setting")
public class SysAiUserSetting {

    /**
     * 主键
     */
    @TableId(value = "sys_ai_user_setting_id", type = IdType.AUTO)
    private Long id;

    /**
     * 归属用户标识（登录用户名）
     */
    @TableField("sys_ai_user_setting_user_id")
    private String userId;

    /**
     * 设置分组，与全局设置同名分组对齐
     */
    @TableField("sys_ai_user_setting_group")
    private String settingGroup;

    /**
     * 字段名
     */
    @TableField("sys_ai_user_setting_field")
    private String field;

    /**
     * 字段值
     */
    @TableField("sys_ai_user_setting_value")
    private String value;

    /**
     * 值类型（STRING / NUMBER / BOOLEAN）
     */
    @TableField("sys_ai_user_setting_value_type")
    private String valueType;

    /**
     * 字段描述
     */
    @TableField("sys_ai_user_setting_description")
    private String description;

    /**
     * 排序值
     */
    @TableField("sys_ai_user_setting_sort")
    private Integer sort;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_user_setting_status")
    private Integer status;

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
