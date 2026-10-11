package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词形态定义实体，对应表 {@code sys_ai_prompt_form}。
 *
 * <p>形态是「角色提示词按哪种执行风格走」的抽象，例如 build（构建）/ plan（规划），
 * 也可以由用户自定义增加第三种。会话里切换的形态、角色编辑器里能编辑的提示词，
 * 用的是<b>同一份</b>形态数据，避免出现「配了却选不到」或「选了却不生效」。</p>
 *
 * <p>{@link #formKey} 为 {@code build} 的形态是内置的且恒存：会话在形态缺失或
 * 数据异常时必须有个确定的兜底，否则整条提示词回退链会断。</p>
 *
 * @author CH
 */
@Data
@TableName("sys_ai_prompt_form")
public class SysAiPromptForm {

    /**
     * 内置形态标识：构建。任何时候都存在，且不可改名/改 key/删除。
     */
    public static final String FORM_KEY_BUILD = "build";

    /**
     * 主键标识
     */
    @TableId(value = "sys_ai_prompt_form_id", type = IdType.AUTO)
    private Integer id;

    /**
     * 归属用户标识（多用户隔离）
     */
    @TableField("user_id")
    private String userId;

    /**
     * 形态标识，如 build / plan / review；会话的 runMode 存的就是它
     */
    @TableField("form_key")
    private String formKey;

    /**
     * 形态中文名，如 构建 / 规划
     */
    @TableField("form_name")
    private String formName;

    /**
     * 图标标识（前端图标注册表 key）
     */
    @TableField("form_icon")
    private String formIcon;

    /**
     * 形态说明，展示在会话的形态切换器里
     */
    @TableField("form_description")
    private String formDescription;

    /**
     * 是否内置形态：1=内置（build 恒存，不可删除）
     */
    @TableField("sys_ai_prompt_form_builtin")
    private Integer builtin;

    /**
     * 排序，小的在前
     */
    @TableField("form_sort")
    private Integer sort;

    /**
     * 状态：1 启用，0 禁用
     */
    @TableField("sys_ai_prompt_form_status")
    private Integer status;

    /**
     * 逻辑删除标记：0 未删除，1 已删除
     */
    @TableField("sys_ai_prompt_form_delete")
    @TableLogic
    private Integer deleted;

    /**
     * 创建名称
     */
    @TableField(fill = FieldFill.INSERT)
    private String createName;

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
