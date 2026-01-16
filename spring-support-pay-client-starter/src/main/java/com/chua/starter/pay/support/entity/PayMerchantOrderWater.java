package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.handler.PayOrderStatusTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 *
 * @author CH
 * @since 2025/10/14 11:28
 */
/**
 * 订单流水表
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description="订单流水表")
@Data
@TableName(value = "pay_merchant_order_water")
public class PayMerchantOrderWater extends SysBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "pay_merchant_order_water_id", type = IdType.AUTO)
    @Schema(description="")
    private Integer payMerchantOrderWaterId;

    /**
     * 流水编号
     */
    @TableField(value = "pay_merchant_order_water_code")
    @Schema(description="流水编号")
    private String payMerchantOrderWaterCode;

    /**
     * 订单编号
     */
    @TableField(value = "pay_merchant_order_code")
    @Schema(description="订单编号")
    private String payMerchantOrderCode;

    /**
     * 订单状态
     */
    @TableField(value = "pay_merchant_order_status", typeHandler = PayOrderStatusTypeHandler.class)
    @Schema(description="订单状态")
    private PayOrderStatus payMerchantOrderStatus;

}
