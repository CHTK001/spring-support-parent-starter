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
 * 支付支付宝配置
 *
 * @author CH
 * @since 2025/10/15 11:23
 */
@Schema(description = "支付支付宝配置")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pay_merchant_config_alipay")
public class PayMerchantConfigAlipay extends SysBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "pay_merchant_config_alipay_id", type = IdType.AUTO)
    @Schema(description = "配置ID")
    private Integer payMerchantConfigAlipayId;

    /**
     * 支付宝应用ID
     */
    @TableField(value = "pay_merchant_config_alipay_app_id")
    @Schema(description = "支付宝应用ID")
    private String payMerchantConfigAlipayAppId;

    /**
     * 商户私钥
     */
    @TableField(value = "pay_merchant_config_alipay_private_key")
    @Schema(description = "商户私钥")
    private String payMerchantConfigAlipayPrivateKey;

    /**
     * 支付宝公钥
     */
    @TableField(value = "pay_merchant_config_alipay_public_key")
    @Schema(description = "支付宝公钥")
    private String payMerchantConfigAlipayPublicKey;

    /**
     * 支付类型
     */
    @TableField(value = "pay_merchant_config_alipay_trade_type")
    @Schema(description = "支付类型")
    private String payMerchantConfigAlipayTradeType;

    /**
     * 支付回调地址
     */
    @TableField(value = "pay_merchant_config_alipay_pay_notify_url")
    @Schema(description = "支付回调地址")
    private String payMerchantConfigAlipayPayNotifyUrl;

    /**
     * 退款回调地址
     */
    @TableField(value = "pay_merchant_config_alipay_refund_notify_url")
    @Schema(description = "退款回调地址")
    private String payMerchantConfigAlipayRefundNotifyUrl;

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
    @TableField(value = "pay_merchant_config_alipay_sign_type")
    @Schema(description = "签名类型")
    private String payMerchantConfigAlipaySignType;

    /**
     * 字符编码格式
     */
    @TableField(value = "pay_merchant_config_alipay_charset")
    @Schema(description = "字符编码格式")
    private String payMerchantConfigAlipayCharset;

    /**
     * 网关地址
     */
    @TableField(value = "pay_merchant_config_alipay_gateway_url")
    @Schema(description = "网关地址")
    private String payMerchantConfigAlipayGatewayUrl;
}

