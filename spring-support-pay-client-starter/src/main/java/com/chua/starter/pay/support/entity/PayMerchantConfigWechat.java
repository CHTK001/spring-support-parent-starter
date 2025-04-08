package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
 * 支付微信支付配置
 */
@ApiModel(description = "支付微信支付配置")
@Schema(description = "支付微信支付配置")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pay_merchant_config_wechat")
public class PayMerchantConfigWechat extends SysBase implements Serializable {
    @TableId(value = "pay_merchant_config_wechat_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "配置编码不能为空", groups = {UpdateGroup.class})
    private Integer payMerchantConfigWechatId;

    /**
     * 微信appId
     */
    @TableField(value = "pay_merchant_config_wechat_app_id")
    @ApiModelProperty(value = "微信appId")
    @Schema(description = "微信appId")
    @Size(max = 255, message = "微信appId最大长度要小于 255")
    @NotBlank(message = "微信appId不能为空", groups = {AddGroup.class})
    private String payMerchantConfigWechatAppId;

    /**
     * 机器ID
     */
    @TableField(value = "pay_merchant_config_wechat_mch_id")
    @ApiModelProperty(value = "机器ID")
    @Schema(description = "机器ID")
    @Size(max = 255, message = "机器ID最大长度要小于 255")
    @NotBlank(message = "机器ID不能为空", groups = {AddGroup.class})
    private String payMerchantConfigWechatMchId;

    /**
     * 序列
     */
    @TableField(value = "pay_merchant_config_wechat_mch_serial_no")
    @ApiModelProperty(value = "序列")
    @Schema(description = "序列")
    @Size(max = 255, message = "序列最大长度要小于 255")
    @NotBlank(message = "序列不能为空", groups = {AddGroup.class})
    private String payMerchantConfigWechatMchSerialNo;

    /**
     * 微信Secret
     */
    @TableField(value = "pay_merchant_config_wechat_app_secret")
    @ApiModelProperty(value = "微信Secret")
    @Schema(description = "微信Secret")
    @Size(max = 255, message = "微信Secret最大长度要小于 255")
    @NotBlank(message = "微信Secret不能为空", groups = {AddGroup.class})
    private String payMerchantConfigWechatAppSecret;

    /**
     * 支付类型;
     */
    @TableField(value = "pay_merchant_config_wechat_trade_type")
    @ApiModelProperty(value = "支付类型; ")
    @Schema(description = "支付类型; ")
    @Size(max = 255, message = "支付类型; 最大长度要小于 255")
    @NotBlank(message = "支付类型不能为空", groups = {AddGroup.class})
    private String payMerchantConfigWechatTradeType;

    /**
     * 回调地址
     */
    @TableField(value = "pay_merchant_config_wechat_notify_url")
    @ApiModelProperty(value = "回调地址")
    @Schema(description = "回调地址")
    @Size(max = 255, message = "回调地址最大长度要小于 255")
    @NotBlank(message = "回调地址不能为空", groups = {AddGroup.class})
    private String payMerchantConfigWechatNotifyUrl;

    /**
     * api v3Key
     */
    @TableField(value = "pay_merchant_config_wechat_api_key_v3")
    @ApiModelProperty(value = "api v3Key")
    @Schema(description = "api v3Key")
    @Size(max = 255, message = "api v3Key最大长度要小于 255")
    @NotBlank(message = "api v3Key不能为空", groups = {AddGroup.class})
    private String payMerchantConfigWechatApiKeyV3;

    @TableField(value = "pay_merchant_config_wechat_private_key_path")
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @Size(max = 255, message = "最大长度要小于 255")
    private String payMerchantConfigWechatPrivateKeyPath;

    /**
     * 是否启用；0:禁用
     */
    @TableField(value = "pay_merchant_config_status")
    @ApiModelProperty(value = "是否启用；0:禁用")
    @Schema(description = "是否启用；0:禁用")
    private Integer payMerchantConfigStatus;

    /**
     * 测试账号
     */
    @TableField(value = "pay_merchant_config_test_account")
    @ApiModelProperty(value = "测试账号")
    @Schema(description = "测试账号")
    private String payMerchantConfigTestAccount;

    /**
     * 商户ID
     */
    @TableField(value = "pay_merchant_id")
    @ApiModelProperty(value = "商户ID")
    @Schema(description = "商户ID")
    @NotBlank(message = "商户ID不能为空", groups = {AddGroup.class})
    private Integer payMerchantId;

    /**
     * 退款回调地址
     */
    @TableField(value = "pay_merchant_config_wechat_refund_notify_url")
    @ApiModelProperty(value = "退款回调地址")
    @Schema(description = "退款回调地址")
    @Size(max = 255, message = "退款回调地址最大长度要小于 255")
    private String payMerchantConfigWechatRefundNotifyUrl;
}