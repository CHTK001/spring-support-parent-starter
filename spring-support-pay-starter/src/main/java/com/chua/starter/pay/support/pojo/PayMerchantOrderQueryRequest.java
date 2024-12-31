package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.SelectGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户订单查询
 * @author CH
 * @since 2024/12/30
 */
@Data
@Schema(description = "商户订单查询")
public class PayMerchantOrderQueryRequest {

    /**
     * 商户编码
     */
    @Schema(description = "商户编码")
    @NotBlank(message = "商户编码不能为空", groups = {SelectGroup.class})
    private String payMerchantCode;

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
