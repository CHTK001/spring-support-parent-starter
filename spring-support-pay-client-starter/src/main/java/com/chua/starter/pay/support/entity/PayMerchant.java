package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.SaveGroup;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 *
 * @author CH
 * @since 2025/10/14 11:28
 */
/**
 * 支付商户管理
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description="支付商户管理")
@Data
@TableName(value = "pay_merchant")
public class PayMerchant extends SysBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "pay_merchant_id", type = IdType.AUTO)
    @Schema(description="")
    private Integer payMerchantId;

    /**
     * 商户名称
     */
    @TableField(value = "pay_merchant_name")
    @Schema(description="商户名称")
    @NotBlank(message = "商户名称不能为空", groups = {SaveGroup.class})
    private String payMerchantName;

    /**
     * 商户编码
     */
    @TableField(value = "pay_merchant_code")
    @Schema(description="商户编码")
    private String payMerchantCode;

    /**
     * 备注
     */
    @TableField(value = "pay_merchant_remark")
    @Schema(description="备注")
    private String payMerchantRemark;

    /**
     * 是否启用; 0:未启用
     */
    @TableField(value = "pay_merchant_status")
    @Schema(description="是否启用; 0:未启用")
    private Integer payMerchantStatus;

    /**
     * 是否删除;0:未删除
     */
    @TableField(value = "pay_merchant_delete")
    @Schema(description="是否删除;0:未删除")
    private Integer payMerchantDelete;

    /**
     * 是否启用钱包; 0:未启用
     */
    @TableField(value = "pay_merchant_open_wallet")
    @Schema(description="是否启用钱包; 0:未启用")
    private Integer payMerchantOpenWallet;
}