package com.chua.starter.pay.support.pojo;

import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayRefundStatus;
import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单退款
 * @author CH
 * @since 2025/10/14 16:31
 */
@Data
@Schema(description = "订单退款")
public class RefundOrderV2Response {

    /**
     * 退款单号
     */
    private PayRefundStatus status;

    /**
     * 订单
     */
    private PayMerchantOrder order;

    /**
     * 退款成功时间
     * 说明：退款成功时间，退款状态status为SUCCESS（退款成功）时，返回该字段。遵循rfc3339标准格式，格式为YYYY-MM-DDTHH:mm:ss+TIMEZONE，YYYY-MM-DD表示年月日，T出现在字符串中，表示time元素的开头，HH:mm:ss表示时分秒，TIMEZONE表示时区（+08:00表示东八区时间，领先UTC
     * 8小时，即北京时间）。例如：2015-05-20T13:29:35+08:00表示，北京时间2015年5月20日13点29分35秒。
     */
    @SerializedName("success_time")
    private String successTime;

    /**
     * 退款创建时间
     * 说明：退款受理时间，遵循rfc3339标准格式，格式为YYYY-MM-DDTHH:mm:ss+TIMEZONE，YYYY-MM-DD表示年月日，T出现在字符串中，表示time元素的开头，HH:mm:ss表示时分秒，TIMEZONE表示时区（+08:00表示东八区时间，领先UTC
     * 8小时，即北京时间）。例如：2015-05-20T13:29:35+08:00表示，北京时间2015年5月20日13点29分35秒。
     */
    @SerializedName("create_time")
    private String createTime;
    /** 微信支付退款号 说明：微信支付退款号 */
    @SerializedName("refund_id")
    private String refundId;

    /** 商户退款单号 说明：商户系统内部的退款单号，商户系统内部唯一，只能是数字、大小写字母_-|*@ ，同一退款单号多次请求只退一笔。 */
    @SerializedName("out_refund_no")
    private String outRefundNo;

    /** 微信支付订单号 说明：微信支付交易订单号 */
    @SerializedName("transaction_id")
    private String transactionId;

    /** 商户订单号 说明：原支付交易对应的商户订单号 */
    @SerializedName("out_trade_no")
    private String outTradeNo;

    /**
     * 退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱
     * 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通
     */
    @SerializedName("user_received_account")
    private String userReceivedAccount;

}
