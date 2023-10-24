package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 设备类型
 */
@Data
@TableName(value = "device_type")
public class DeviceType implements Serializable {
    @TableId(value = "device_type_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer deviceTypeId;

    /**
     * 编码
     */
    @TableField(value = "device_type_code")
    @Size(max = 255, message = "编码最大长度要小于 255")
    private String deviceTypeCode;

    /**
     * 类型名称
     */
    @TableField(value = "device_type_name")
    @Size(max = 255, message = "类型名称最大长度要小于 255")
    private String deviceTypeName;

    /**
     * 父节点
     */
    @TableField(value = "device_type_parent")
    @Size(max = 255, message = "父节点最大长度要小于 255")
    private String deviceTypeParent;

    /**
     * 备注
     */
    @TableField(value = "device_type_remark")
    @Size(max = 255, message = "备注最大长度要小于 255")
    private String deviceTypeRemark;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 节点路径
     */
    @TableField(value = "device_type_path")
    @Size(max = 255, message = "节点路径最大长度要小于 255")
    private String deviceTypePath;

    private static final long serialVersionUID = 1L;
}