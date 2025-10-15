package com.chua.starter.pay.support.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.chua.starter.pay.support.enums.PayTradeType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单
 *
 * @author CH
 * @since 2025/10/14 13:09
 */
@Data
@Schema(name = "创建订单")
public class CreateOrderV2Request {

    /**
     * 交易类型
     */
    @Schema(name = "交易类型")
    private PayTradeType payTradeType;
    /**
     * 商户ID
     */
    @Schema(name = "商户ID")
    private Integer payMerchantId;

    /**
     * 订单金额
     */
    @Schema(name = "订单金额")
    private BigDecimal amount;

    /**
     * 请求ID
     */
    @Schema(name = "请求ID")
    private String requestId;
    /**
     * 原始数据ID
     */
    @Schema(name = "原始数据ID")
    private String originalDataId;

    /**
     * 订单类型
     */
    @TableField(value = "pay_merchant_order_type")
    @Schema(description="订单类型")
    private Integer payMerchantOrderType;

    /**
     * 订单项目
     */
    @TableField(value = "pay_merchant_order_project")
    @Schema(description="订单项目")
    private Integer payMerchantOrderProject;
    /**
     * 是否有交易类型
     *
     * @return {@link boolean}
     */
    public boolean hasTradeType() {
        return null != payTradeType;
    }

    /**
     * 是否有金额
     *
     * @return {@link boolean}
     */
    public boolean hasAmount() {
        if(payTradeType == PayTradeType.PAY_WALLET) {
            return null != amount;
        }
        return null != amount && amount.compareTo(BigDecimal.ZERO) > 0;
    }



    /**
     * 克隆并获取
     *
     * @return {@link CreateOrderV2Request}
     */
    public CreateOrderV2Request cloneAndGet() {
        CreateOrderV2Request request = new CreateOrderV2Request();
        request.setPayMerchantId(payMerchantId);
        request.setPayMerchantOrderProject(payMerchantOrderProject);
        request.setPayMerchantOrderType(payMerchantOrderType);
        request.setPayTradeType(payTradeType);
        request.setAmount(amount);
        request.setRequestId(requestId);
        request.setOriginalDataId(originalDataId);
        return request;
    }
}
