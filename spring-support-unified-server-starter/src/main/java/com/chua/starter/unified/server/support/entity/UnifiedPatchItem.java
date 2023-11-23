package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

/**
 *    
 * @author CH
 */

/**
 * 补丁管理
 */
@Data
@TableName(value = "unified_patch_item")
public class UnifiedPatchItem implements Serializable {
    @TableId(value = "unified_patch_item_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedPatchItemId;

    /**
     * 执行器ID
     */
    @TableField(value = "unified_executer_id")
    private Integer unifiedExecuterId;

    /**
     * 修补程序文件
     */
    @TableField(exist = false)
    private String patchFile;

    @TableField(exist = false)
    private String unifiedAppname;

    /**
     * 补丁包id
     */
    @TableField(value = "unified_patch_id")
    private Integer unifiedPatchId;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}