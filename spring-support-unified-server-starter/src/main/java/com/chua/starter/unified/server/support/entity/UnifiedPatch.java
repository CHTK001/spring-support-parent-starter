package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 *    
 * @author CH
 */

/**
 * 补丁管理
 */
@Data
@TableName(value = "unified_patch")
public class UnifiedPatch implements Serializable {
    @TableId(value = "unified_patch_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedPatchId;

    /**
     * 补丁英文名称
     */
    @TableField(value = "unified_patch_name")
    @Size(max = 255, message = "补丁英文名称最大长度要小于 255")
    private String unifiedPatchName;

    /**
     * 补丁中文名称
     */
    @TableField(value = "unified_patch_chinese_name")
    @Size(max = 255, message = "补丁中文名称最大长度要小于 255")
    private String unifiedPatchChineseName;

    /**
     * 补丁包名称
     */
    @TableField(value = "unified_patch_pack", updateStrategy = FieldStrategy.IGNORED)
    @Size(max = 255, message = "补丁包名称最大长度要小于 255")
    private String unifiedPatchPack;

    /**
     * 描述
     */
    @TableField(value = "unified_patch_desc")
    @Size(max = 255, message = "描述最大长度要小于 255")
    private String unifiedPatchDesc;

    /**
     * 版本
     */
    @TableField(value = "unified_patch_version")
    @Size(max = 255, message = "版本最大长度要小于 255")
    private String unifiedPatchVersion;

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


    /**
     * 执行器名称
     */
    @TableField(exist = false)
    private List<Integer> executorIds;

    private static final long serialVersionUID = 1L;
}