package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 *    
 * @author CH
 */

/**
 * 设备字典表
 */
@Schema(description = "设备字典表")
@Data
@TableName(value = "device_dict")
public class DeviceDict implements Serializable {
    @TableId(value = "device_dict_id", type = IdType.AUTO)
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer deviceDictId;

    /**
     * 字典名称
     */
    @TableField(value = "device_dict_name")
    @Schema(description = "字典名称")
    @Size(max = 255, message = "字典名称最大长度要小于 255")
    private String deviceDictName;

    /**
     * 字典编码
     */
    @TableField(value = "device_dict_code")
    @Schema(description = "字典编码")
    @Size(max = 255, message = "字典编码最大长度要小于 255")
    private String deviceDictCode;

    /**
     * 字典类型
     */
    @TableField(value = "device_dict_type")
    @Schema(description = "字典类型")
    @Size(max = 255, message = "字典类型最大长度要小于 255")
    private String deviceDictType;

    /**
     * 是否启用；0：禁用
     */
    @TableField(value = "device_dict_status")
    @Schema(description = "是否启用；0：禁用")
    private Integer deviceDictStatus;

    private static final long serialVersionUID = 1L;
}