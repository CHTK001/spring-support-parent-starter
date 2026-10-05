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
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_global_setting")
public class SysAiGlobalSetting {

    /**
     * 全局设置主键 标识
     */
    @TableId(value = "sys_ai_global_setting_id", type = IdType.AUTO)
    /**
     * SYSAI全局设置标识
    */
    private Long id;

    /**
     * 设置分组（如 全局 / runtime / rag）
     */
    @TableField("sys_ai_global_setting_group")
    private String settingGroup; // setting群体

    /**
     * 字段名
     */
    @TableField("sys_ai_global_setting_field")
    private String field; // 字段

    /**
     * 字段值
     */
    @TableField("sys_ai_global_setting_value")
    private String value; // 值

    /**
     * 值类型（字符串 / 数字 / 布尔值）
     */
    @TableField("sys_ai_global_setting_value_type")
    private String valueType; // 值类型

    /**
     * 字段描述
     */
    @TableField("sys_ai_global_setting_description")
    private String description; // description

    /**
     * 排序值
     */
    @TableField("sys_ai_global_setting_sort")
    private Integer sort; // 排序

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_global_setting_status")
    private Integer status; // 状态

    /**
     * 创建时间
     */
    @TableField("create_time")
    /**
     * 创建时间
    */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    /**
     * 更新时间
    */
    private LocalDateTime updateTime;

    /**
     * 创建人（用户名）。
     *
     * <p><b>为空即代表「这行从未被人配置过」，是系统内置默认值</b>。
     * 这一列此前没被实体映射，导致写入路径也无法记录操作人，
     * 于是 2026-09-30 那批一次性种子与管理员后来手工改过的值在库里完全一样，
     * 界面上只能显示「继承全局：xxx」，把系统默认说成别人配的。</p>
     */
    @TableField("create_name")
    /**
     * 创建人
     */
    private String createName;

    /**
     * 更新人（用户名）；同样为空表示从未被人工修改过。
     */
    @TableField("update_name")
    /**
     * 更新人
     */
    private String updateName;
}

