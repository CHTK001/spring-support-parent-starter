package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@TableName(value = "unified_component_item")
public class UnifiedComponentItem implements Serializable {
    @TableId(value = "unified_component_item_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedComponentItemId;

    /**
     * 组件ID
     */
    @TableField(value = "unified_component_id")
    @Size(max = 255,message = "组件ID最大长度要小于 255")
    private String unifiedComponentId;

    /**
     * 安装的应用
     */
    @TableField(value = "unified_component_item_appname")
    @Size(max = 255,message = "安装的应用最大长度要小于 255")
    private String unifiedComponentItemAppname;

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