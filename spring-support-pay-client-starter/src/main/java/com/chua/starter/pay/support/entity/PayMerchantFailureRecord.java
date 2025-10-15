package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 *
 * @author CH
 * @since 2025/10/15 9:50
 */
/**
 * 支付失败记录
 */
@Schema(description="支付失败记录")
@Data
@TableName(value = "pay_merchant_failure_record")
public class PayMerchantFailureRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "pay_merchant_failure_record_id", type = IdType.AUTO)
    @Schema(description="")
    private Integer payMerchantFailureRecordId;

    /**
     * 消息体
     */
    @TableField(value = "pay_merchant_failure_record_body")
    @Schema(description="消息体")
    private String payMerchantFailureRecordBody;

    /**
     * 签名
     */
    @TableField(value = "pay_merchant_failure_record_signature")
    @Schema(description="签名")
    private String payMerchantFailureRecordSignature;

    /**
     * 签名类型
     */
    @TableField(value = "pay_merchant_failure_record_signature_type")
    @Schema(description="签名类型")
    private String payMerchantFailureRecordSignatureType;

    /**
     * 单次请求ID
     */
    @TableField(value = "pay_merchant_failure_record_nonce")
    @Schema(description="单次请求ID")
    private String payMerchantFailureRecordNonce;

    /**
     * 证书
     */
    @TableField(value = "pay_merchant_failure_record_serial")
    @Schema(description="证书")
    private String payMerchantFailureRecordSerial;

    /**
     * 订单编号
     */
    @TableField(value = "pay_merchant_merchant_order_code")
    @Schema(description="订单编号")
    private String payMerchantMerchantOrderCode;

    /**
     * 时间戳
     */
    @TableField(value = "pay_merchant_failure_record_timestamp")
    @Schema(description="时间戳")
    private Integer payMerchantFailureRecordTimestamp;

    /**
     * 失败原因
     */
    @TableField(value = "pay_merchant_failure_reason")
    @Schema(description="失败原因")
    private String payMerchantFailureReason;

    /**
     * 失败类型
     */
    @TableField(value = "pay_merchant_failure_type")
    @Schema(description="失败类型")
    private String payMerchantFailureType;

    /**
     * 创建人姓名
     */
    @TableField(value = "create_name")
    @Schema(description="创建人姓名")
    private String createName;

    /**
     * 创建人
     */
    @TableField(value = "create_by")
    @Schema(description="创建人")
    private Integer createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @Schema(description="创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @Schema(description="更新时间")
    private LocalDateTime updateTime;

    /**
     * 更新人姓名
     */
    @TableField(value = "update_name")
    @Schema(description="更新人姓名")
    private String updateName;

    /**
     * 更新人
     */
    @TableField(value = "update_by")
    @Schema(description="更新人")
    private Integer updateBy;
}