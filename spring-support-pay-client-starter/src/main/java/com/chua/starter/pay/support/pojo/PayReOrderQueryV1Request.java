package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.SelectGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 请求
 * @author CH
 * @since 2024/12/30
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "查询待支付订单")
public class PayReOrderQueryV1Request extends Query<PayMerchantOrder> implements Serializable {


    /**
     * 支付来源
     */
    @Schema(description = "支付来源")
    @NotBlank(message = "支付来源不能为空", groups = {SelectGroup.class})
    private String payMerchantOrderOrigin;


    /**
     * 商户号
     */
    @Schema(description = "商户号")
    @NotBlank(message = "商户号不能为空", groups = {SelectGroup.class})
    private String payMerchantCode;
    /**
     * 用户id
     */
    @Schema(description = "用户id")
    private String userId;
    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
