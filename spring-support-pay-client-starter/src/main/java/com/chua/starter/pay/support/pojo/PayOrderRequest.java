package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.pay.support.emuns.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 请求
 * @author CH
 * @since 2024/12/30
 */
@Data
@Schema(description = "订单请求")
public class PayOrderRequest implements Serializable {


    /**
     * 订单id
     */
    @Schema(description = "订单id(用于前端区分是否同一次交易), 不传不进行订单校验")
    @NotBlank(message = "订单id不能为空", groups = {AddGroup.class})
    private String orderId;

    /**
     * 是否校验部门
     */
    @Schema(description = "是否校验部门")
    private boolean checkDept = false;

    /**
     * 部门id
     */
    @Schema(description = "部门id")
    private String deptId;


    /**
     * 机构组织者
     */
    @Schema(description = "机构组织者")
    private String deptOrganizer;

    /**
     * 部门名称
     */
    @Schema(description = "部门名称")
    private String deptName;
    /**
     * 用户id
     */
    @Schema(description = "用户id(微信为openId, 支付宝账号ID)")
    @NotBlank(message = "用户id不能为空", groups = {AddGroup.class})
    private String userId;

    /**
     * 来源
     */
    @Schema(description = "来源(业务系统ID, 用于处理数据)")
    @NotBlank(message = "来源不能为空", groups = {AddGroup.class})
    private String origin;

    /**
     * 商品名称
     */
    @Schema(description = "商品名称")
    @NotBlank(message = "商品名称不能为空", groups = {AddGroup.class})
    private String productName;


    /**
     * 商品ID
     */
    @Schema(description = "商品编码(用于业务上的商品标识)")
    @NotBlank(message = "商品编码不能为空", groups = {AddGroup.class})
    private String productCode;
    /**
     * 商户编码
     */
    @Schema(description = "商户编码")
    @NotBlank(message = "商户编码不能为空", groups = {AddGroup.class})
    private String merchantCode;


    /**
     * 交易类型
     */
    @Schema(description = "交易类型")
    @NotBlank(message = "交易类型不能为空", groups = {AddGroup.class})
    private TradeType tradeType;

    /**
     * 金额
     */
    @Schema(description = "金额(原始金额)")
    @NotBlank(message = "金额不能为空", groups = {AddGroup.class})
    private BigDecimal price;

    /**
     * 总金额
     */
    @Schema(description = "总金额(实际支付金额)")
    @NotBlank(message = "总金额不能为空", groups = {AddGroup.class})
    private BigDecimal totalPrice;

    /**
     * 优惠券编码
     */
    @Schema(description = "优惠券编码")
    private String couponCode;
    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;

    /**
     * 附加信息
     */
    @Schema(description = "附加信息(业务数据)")
    private String attach;
}
