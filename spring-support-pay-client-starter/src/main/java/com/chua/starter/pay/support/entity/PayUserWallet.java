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
 * @since 2025/10/14 11:28
 */
/**
 * 用户钱包
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description="用户钱包")
@Data
@TableName(value = "pay_user_wallet")
public class PayUserWallet extends SysBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "pay_user_wallet_id", type = IdType.AUTO)
    @Schema(description="")
    private Integer payUserWalletId;

    /**
     * 金额
     */
    @TableField(value = "pay_user_wallet_amount")
    @Schema(description="金额")
    private BigDecimal payUserWalletAmount;

    /**
     * 账号ID
     */
    @TableField(value = "user_id")
    @Schema(description="账号ID")
    private String userId;

    /**
     * 账号类型
     */
    @TableField(value = "user_type")
    @Schema(description="账号类型")
    private String userType;

    /**
     * 最后使用时间
     */
    @TableField(value = "pay_user_wallet_last_time")
    @Schema(description="最后使用时间")
    private LocalDateTime payUserWalletLastTime;

    /**
     * 最后一次订单号
     */
    @TableField(value = "pay_user_wallet_last_order_code")
    @Schema(description="最后一次订单号")
    private String payUserWalletLastOrderCode;
}
