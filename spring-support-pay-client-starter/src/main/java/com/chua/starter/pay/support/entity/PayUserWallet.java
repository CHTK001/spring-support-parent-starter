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
import java.math.BigDecimal;

/**
    * 用户钱包
    */
@ApiModel(description="用户钱包")
@Schema(description="用户钱包")
@Data
@EqualsAndHashCode(callSuper=true)
@TableName(value = "pay_user_wallet")
public class PayUserWallet extends SysBase implements Serializable {
    @TableId(value = "pay_user_wallet_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer payUserWalletId;

    /**
     * 用户ID
     */
    @TableField(value = "pay_user_wallet_user_id")
    @ApiModelProperty(value="用户ID")
    @Schema(description="用户ID")
    @Size(max = 255,message = "用户ID最大长度要小于 255")
    private String payUserWalletUserId;

    /**
     * 钱包
     */
    @TableField(value = "pay_user_wallet_money")
    @ApiModelProperty(value="钱包")
    @Schema(description="钱包")
    private BigDecimal payUserWalletMoney;

    /**
     * 锁
     */
    @TableField(value = "pay_user_wallet_version")
    @ApiModelProperty(value="锁")
    @Schema(description="锁")
    @Version
    private Integer payUserWalletVersion;
}