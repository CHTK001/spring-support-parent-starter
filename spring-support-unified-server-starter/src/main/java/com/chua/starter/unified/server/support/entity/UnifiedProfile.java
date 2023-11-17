package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 *    
 * @author CH
 */     
/**
    * 配置字典
    */
@Data
@TableName(value = "unified_profile")
public class UnifiedProfile implements Serializable {
    @TableId(value = "unified_profile_id", type = IdType.AUTO)
    private Integer unifiedProfileId;

    /**
     * 名称
     */
    @TableField(value = "unified_profile_name")
    @Size(max = 255,message = "名称最大长度要小于 255")
    private String unifiedProfileName;

    /**
     * 类型
     */
    @TableField(value = "unified_profile_type")
    @Size(max = 255,message = "类型最大长度要小于 255")
    private String unifiedProfileType;

    /**
     * 描述
     */
    @TableField(value = "unified_profile_desc")
    @Size(max = 255,message = "描述最大长度要小于 255")
    private String unifiedProfileDesc;

    @TableField(value = "create_time")
    private Date createTime;

    private static final long serialVersionUID = 1L;
}