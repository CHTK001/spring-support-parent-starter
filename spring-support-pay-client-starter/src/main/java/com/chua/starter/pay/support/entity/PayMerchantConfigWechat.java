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
 * @since 2025/10/15 11:23
 */

/**
 * 支付微信支付配置
 */
@Schema(description = "支付微信支付配置")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pay_merchant_config_wechat")
public class PayMerchantConfigWechat extends SysBase implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "pay_merchant_config_wechat_id", type = IdType.AUTO)
    @Schema(description = "")
    private Integer payMerchantConfigWechatId;

    /**
     * 微信appId
     */
    @TableField(value = "pay_merchant_config_wechat_app_id")
    @Schema(description = "微信appId")
    private String payMerchantConfigWechatAppId;

    /**
     * 机器ID
     */
    @TableField(value = "pay_merchant_config_wechat_mch_id")
    @Schema(description = "机器ID")
    private String payMerchantConfigWechatMchId;

    /**
     * 序列
     */
    @TableField(value = "pay_merchant_config_wechat_mch_serial_no")
    @Schema(description = "序列")
    private String payMerchantConfigWechatMchSerialNo;

    /**
     * 微信Secret
     */
    @TableField(value = "pay_merchant_config_wechat_app_secret")
    @Schema(description = "微信Secret")
    private String payMerchantConfigWechatAppSecret;

    /**
     * 支付类型;
     */
    @TableField(value = "pay_merchant_config_wechat_trade_type")
    @Schema(description = "支付类型; ")
    private String payMerchantConfigWechatTradeType;

    /**
     * 回调地址
     */
    @TableField(value = "pay_merchant_config_wechat_notify_url")
    @Schema(description = "回调地址")
    private String payMerchantConfigWechatNotifyUrl;

    /**
     * api v3Key
     */
    @TableField(value = "pay_merchant_config_wechat_api_key_v3")
    @Schema(description = "api v3Key")
    private String payMerchantConfigWechatApiKeyV3;

    /**
     * 私有key
     */
    @TableField(value = "pay_merchant_config_wechat_private_key_path")
    @Schema(description = "私有key")
    private String payMerchantConfigWechatPrivateKeyPath;

    /**
     * 支付分服务ID
     */
    @TableField(value = "pay_merchant_config_wechat_payment_point_service_id")
    @Schema(description = "支付分服务ID")
    private String payMerchantConfigWechatPaymentPointServiceId;

    /**
     * 支付分服务名称
     */
    @TableField(value = "pay_merchant_config_wechat_payment_point_service_name")
    @Schema(description = "支付分服务名称")
    private String payMerchantConfigWechatPaymentPointServiceName;

    /**
     * 支付分风险金额度(单位:分)
     */
    @TableField(value = "pay_merchant_config_wechat_payment_point_risk_amount")
    @Schema(description = "支付分风险金额度(单位:分)")
    private BigDecimal payMerchantConfigWechatPaymentPointRiskAmount;

    /**
     * 支付分规则
     */
    @TableField(value = "pay_merchant_config_wechat_payment_point_rule")
    @Schema(description = "支付分规则")
    private String payMerchantConfigWechatPaymentPointRule;

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
     * 支付回调地址
     */
    @TableField(value = "pay_merchant_config_wechat_pay_notify_url")
    @Schema(description = "支付回调地址")
    private String payMerchantConfigWechatPayNotifyUrl;

    /**
     * 退款回调地址
     */
    @TableField(value = "pay_merchant_config_wechat_refund_notify_url")
    @Schema(description = "退款回调地址")
    private String payMerchantConfigWechatRefundNotifyUrl;

    /**
     * 转账回调地址
     */
    @TableField(value = "pay_merchant_config_wechat_transfer_url")
    @Schema(description = "转账回调地址")
    private String payMerchantConfigWechatTransferUrl;

    /**
     * 支付分回调地址
     */
    @TableField(value = "pay_merchant_config_wechat_payment_point_notify_url")
    @Schema(description = "支付分回调地址")
    private String payMerchantConfigWechatPaymentPointNotifyUrl;
}
