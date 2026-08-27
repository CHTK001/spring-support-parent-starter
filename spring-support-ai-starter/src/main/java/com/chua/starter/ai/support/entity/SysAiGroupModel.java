package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分组-模型关联实体。
 *
 * @author CH
 */
@Data
@TableName("sys_ai_group_model")
public class SysAiGroupModel {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    /** SYSAI分组模型ID */
    private Integer id;

    /**
     * 分组 ID
     */
    private Integer sysAiGroupId;

    /**
     * 模块 ID
     */
    private Integer sysAiModuleId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    /** Create时间 */
    private LocalDateTime createTime;
}

