package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 云闪付支付配置
 *
 * @author CH
 * @since 2025/10/15 11:23
 */
@Schema(description = "云闪付支付配置")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pay_merchant_config_unionpay")
public class PayMerchantConfigUnionPay extends SysBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "pay_merchant_config_unionpay_id", type = IdType.AUTO)
    @Schema(description = "配置ID")
    private Integer payMerchantConfigUnionPayId;

    /**
     * 云闪付应用ID
     */
    @TableField(value = "pay_merchant_config_unionpay_app_id")
    @Schema(description = "云闪付应用ID")
    private String payMerchantConfigUnionPayAppId;

    /**
     * 商户号
     */
    @TableField(value = "pay_merchant_config_unionpay_merchant_id")
    @Schema(description = "商户号")
    private String payMerchantConfigUnionPayMerchantId;

    /**
     * 商户私钥
     */
    @TableField(value = "pay_merchant_config_unionpay_private_key")
    @Schema(description = "商户私钥")
    private String payMerchantConfigUnionPayPrivateKey;

    /**
     * 云闪付公钥
     */
    @TableField(value = "pay_merchant_config_unionpay_public_key")
    @Schema(description = "云闪付公钥")
    private String payMerchantConfigUnionPayPublicKey;

    /**
     * 支付类型
     */
    @TableField(value = "pay_merchant_config_unionpay_trade_type")
    @Schema(description = "支付类型")
    private String payMerchantConfigUnionPayTradeType;

    /**
     * 支付回调地址
     */
    @TableField(value = "pay_merchant_config_unionpay_pay_notify_url")
    @Schema(description = "支付回调地址")
    private String payMerchantConfigUnionPayPayNotifyUrl;

    /**
     * 退款回调地址
     */
    @TableField(value = "pay_merchant_config_unionpay_refund_notify_url")
    @Schema(description = "退款回调地址")
    private String payMerchantConfigUnionPayRefundNotifyUrl;

    /**
     * 是否启用；0:禁用
     */
    @TableField(value = "pay_merchant_config_status")
    @Schema(description = "是否启用；0:禁用")
    private Integer payMerchantConfigStatus;

    /**
     * 测试账号
     */
    @TableField(value = "pay_merchant_config_test_account")
    @Schema(description = "测试账号")
    private String payMerchantConfigTestAccount;

    /**
     * 商户ID
     */
    @TableField(value = "pay_merchant_id")
    @Schema(description = "商户ID")
    private Integer payMerchantId;

    /**
     * 签名类型
     */
    @TableField(value = "pay_merchant_config_unionpay_sign_type")
    @Schema(description = "签名类型")
    private String payMerchantConfigUnionPaySignType;

    /**
     * 字符编码格式
     */
    @TableField(value = "pay_merchant_config_unionpay_charset")
    @Schema(description = "字符编码格式")
    private String payMerchantConfigUnionPayCharset;

    /**
     * 网关地址
     */
    @TableField(value = "pay_merchant_config_unionpay_gateway_url")
    @Schema(description = "网关地址")
    private String payMerchantConfigUnionPayGatewayUrl;
}

