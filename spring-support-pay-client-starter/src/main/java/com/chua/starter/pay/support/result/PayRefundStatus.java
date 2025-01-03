package com.chua.starter.pay.support.result;

import com.google.gson.annotations.SerializedName;

/**
 * 退款状态
 * @author CH
 * @since 2024/12/30
 */
public enum PayRefundStatus {

    @SerializedName("SUCCESS")
    SUCCESS,

    @SerializedName("CLOSED")
    CLOSED,

    @SerializedName("PROCESSING")
    PROCESSING,

    @SerializedName("ABNORMAL")
    ABNORMAL
}
