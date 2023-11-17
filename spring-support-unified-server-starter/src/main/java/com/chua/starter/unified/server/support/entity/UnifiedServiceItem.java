package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 * @author CH
 */
@Data
@TableName(value = "unified_service_item")
public class UnifiedServiceItem implements Serializable {
    @TableId(value = "unified_service_item_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedServiceItemId;

    /**
     * SPIID
     */
    @TableField(value = "unified_service_id")
    @Size(max = 255, message = "SPIID最大长度要小于 255")
    private String unifiedServiceId;

    /**
     * 安装的应用
     */
    @TableField(value = "unified_service_item_appname")
    @Size(max = 255, message = "安装的应用最大长度要小于 255")
    private String unifiedServiceItemAppname;

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