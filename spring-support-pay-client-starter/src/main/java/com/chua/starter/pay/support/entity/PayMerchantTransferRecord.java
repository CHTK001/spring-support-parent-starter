package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 *
 * @author CH
 * @since 2025/10/15 14:09
 */

/**
 * 转账记录表
 */
@Schema(description = "转账记录表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pay_merchant_transfer_record")
public class PayMerchantTransferRecord extends SysBase implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "pay_merchant_transfer_record_id", type = IdType.AUTO)
    @Schema(description = "")
    private Integer payMerchantTransferRecordId;

    /**
     * 转账用户
     */
    @TableField(value = "pay_merchant_transfer_record_user_openid")
    @Schema(description = "转账用户")
    private String payMerchantTransferRecordUserOpenid;

    /**
     * 金额
     */
    @TableField(value = "pay_merchant_transfer_record_amount")
    @Schema(description = "金额")
    private BigDecimal payMerchantTransferRecordAmount;

    /**
     * 真实姓名(部分要使用)
     */
    @TableField(value = "pay_merchant_transfer_record_real_name")
    @Schema(description = "真实姓名(部分要使用)")
    private String payMerchantTransferRecordRealName;

    /**
     * 手机号
     */
    @TableField(value = "pay_merchant_transfer_record_phone")
    @Schema(description = "手机号")
    private String payMerchantTransferRecordPhone;

    /**
     * 状态
     */
    @TableField(value = "pay_merchant_transfer_record_status")
    @Schema(description = "状态")
    private String payMerchantTransferRecordStatus;

    /**
     * 转账编码
     */
    @TableField(value = "pay_merchant_transfer_record_code")
    @Schema(description = "转账编码")
    private String payMerchantTransferRecordCode;

    /**
     * 失败原因
     */
    @TableField(value = "pay_merchant_transfer_record_reason")
    @Schema(description = "失败原因")
    private String payMerchantTransferRecordReason;

    /**
     * 商户ID
     */
    @TableField(value = "pay_merchant_id")
    @Schema(description = "商户ID")
    private Integer payMerchantId;

    /**
     * 描述
     */
    @TableField(value = "pay_merchant_transfer_record_description")
    @Schema(description = "描述")
    private String payMerchantTransferRecordDescription;

    /**
     * 转账完成时间
     */
    @TableField(value = "pay_merchant_transfer_record_finish_time")
    @Schema(description = "转账完成时间")
    private LocalDateTime payMerchantTransferRecordFinishTime;

    /**
     * 转账创建时间
     */
    @TableField(value = "pay_merchant_transfer_record_create_time")
    @Schema(description = "转账创建时间")
    private LocalDateTime payMerchantTransferRecordCreateTime;
}