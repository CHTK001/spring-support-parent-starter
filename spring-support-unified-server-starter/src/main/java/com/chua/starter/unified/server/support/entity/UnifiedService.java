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
@TableName(value = "unified_service")
public class UnifiedService implements Serializable {
    @TableId(value = "unified_service_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedServiceId;

    /**
     * SPI名称
     */
    @TableField(value = "unified_service_name")
    @Size(max = 255, message = "SPI名称最大长度要小于 255")
    private String unifiedServiceName;

    /**
     * 版本
     */
    @TableField(value = "unified_service_version")
    @Size(max = 255, message = "版本最大长度要小于 255")
    private String unifiedServiceVersion;

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