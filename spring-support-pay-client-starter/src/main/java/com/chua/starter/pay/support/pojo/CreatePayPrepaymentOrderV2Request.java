package com.chua.starter.pay.support.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.pay.support.enums.PayTradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
public class CreatePayPrepaymentOrderV2Request {

    /**
     * 商户ID
     */
    @Schema(name = "商户ID")
    @NotNull(message = "商户ID不能为空", groups = {AddGroup.class})
    private Integer payMerchantId;

    /**
     * 订单金额
     */
    @Schema(name = "订单金额")
    @NotNull(message = "订单金额不能为空", groups = {AddGroup.class})
    private BigDecimal amount;

    /**
     * 请求ID
     */
    @Schema(name = "请求ID")
    @NotNull(message = "请求ID不能为空", groups = {AddGroup.class})
    private String requestId;
    /**
     * 原始数据ID
     */
    @Schema(name = "原始数据ID")
    @NotNull(message = "原始数据ID不能为空", groups = {AddGroup.class})
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
     * 克隆并获取
     *
     * @return {@link CreateOrderV2Request}
     */
    public CreateOrderV2Request cloneAndGet() {
        CreateOrderV2Request request = new CreateOrderV2Request();
        request.setPayMerchantId(payMerchantId);
        request.setPayMerchantOrderProject(payMerchantOrderProject);
        request.setPayMerchantOrderType(payMerchantOrderType);
        request.setAmount(amount);
        request.setRequestId(requestId);
        request.setOriginalDataId(originalDataId);
        return request;
    }
}
