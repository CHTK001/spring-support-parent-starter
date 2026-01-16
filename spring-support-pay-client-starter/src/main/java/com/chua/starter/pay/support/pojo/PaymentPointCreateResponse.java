package com.chua.starter.pay.support.pojo;

import com.chua.starter.pay.support.enums.PaymentPointState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建支付分
 * @author CH
 * @since 2025/5/26 9:11
 */
@Data
@Schema(description = "创建支付分")
public class PaymentPointCreateResponse {

    /**
     * 商户批次单号
     */
    @Schema(description = "商户批次单号")
    private String outBatchNo;
    /**
     * 创建支付分结果
     */
    @Schema(description = "创建支付分结果")
    private PaymentPointState state;

    /**
     * 创建支付分结果描述
     */
    @Schema(description = "创建支付分结果描述")
    private String stateDescription;

    /**
     * [微信支付服务订单号]支付分订单在微信侧的唯一标识，31位数字，开头由1000000000+年月日组成。
     */
    @Schema(description = "[微信支付服务订单号]支付分订单在微信侧的唯一标识，31位数字，开头由1000000000+年月日组成。")
    private String orderId;

    /**
     * [跳转微信侧小程序订单数据]创建支付分订单成功后返回，用于拉起支付分小程序确认订单页面，由数字大小写字母_-符号组成，不超过300字符。
     */
    @Schema(description = "[跳转微信侧小程序订单数据]创建支付分订单成功后返回，用于拉起支付分小程序确认订单页面，由数字大小写字母_-符号组成，不超过300字符。")
    private String packageInfo;
}
