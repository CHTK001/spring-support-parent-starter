package com.chua.starter.pay.support.enums;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

/**
 * 退款状态枚举类
 *
 * @author CH
 * @since 2024/12/30
 */
@Getter
public enum PayRefundStatus {

    /**
     * 退款成功
     */
    @SerializedName("SUCCESS")
    SUCCESS,

    /**
     * 退款关闭
     */
    @SerializedName("CLOSED")
    CLOSED,

    /**
     * 退款处理中
     */
    @SerializedName("PROCESSING")
    PROCESSING,

    /**
     * 退款异常
     */
    @SerializedName("ABNORMAL")
    ABNORMAL
}
