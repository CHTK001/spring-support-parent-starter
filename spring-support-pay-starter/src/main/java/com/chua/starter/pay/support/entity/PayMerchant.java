package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 *
 * @since 2024/12/30
 * @author CH    
 */

/**
 * 支付商户管理
 */
@ApiModel(description = "支付商户管理")
@Schema(description = "支付商户管理")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pay_merchant")
public class PayMerchant extends SysBase implements Serializable {
    @TableId(value = "pay_merchant_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer payMerchantId;

    /**
     * 商户名称
     */
    @TableField(value = "pay_merchant_name")
    @ApiModelProperty(value = "商户名称")
    @Schema(description = "商户名称")
    @Size(max = 255, message = "商户名称最大长度要小于 255")
    private String payMerchantName;

    /**
     * 商户编码
     */
    @TableField(value = "pay_merchant_code")
    @ApiModelProperty(value = "商户编码")
    @Schema(description = "商户编码")
    @Size(max = 255, message = "商户编码最大长度要小于 255")
    private String payMerchantCode;

    /**
     * 备注
     */
    @TableField(value = "pay_merchant_remark")
    @ApiModelProperty(value = "备注")
    @Schema(description = "备注")
    @Size(max = 255, message = "备注最大长度要小于 255")
    private String payMerchantRemark;

    /**
     * 是否启用; 0:未启用
     */
    @TableField(value = "pay_merchant_status")
    @ApiModelProperty(value = "是否启用; 0:未启用")
    @Schema(description = "是否启用; 0:未启用")
    private Integer payMerchantStatus;

    /**
     * 是否删除;0:未删除
     */
    @TableField(value = "pay_merchant_delete")
    @ApiModelProperty(value = "是否删除;0:未删除")
    @Schema(description = "是否删除;0:未删除")
    private @TableLogic(delval = "1", value = "0") Integer payMerchantDelete;
}