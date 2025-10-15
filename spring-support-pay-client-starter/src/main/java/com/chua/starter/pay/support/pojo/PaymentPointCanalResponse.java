package com.chua.starter.pay.support.pojo;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建支付分
 * @author CH
 * @since 2025/5/26 9:11
 */
@NoArgsConstructor
@Data
@Schema(description = "取消支付分")
public class PaymentPointCanalResponse {


    @SerializedName("appid")
    private String appid;
    @SerializedName("mchid")
    private String mchid;
    @SerializedName("out_order_no")
    private String outOrderNo;
    @SerializedName("service_id")
    private String serviceId;
    @SerializedName("order_id")
    private String orderId;
    /**
     * 错误信息
     */
    private String error;
}
