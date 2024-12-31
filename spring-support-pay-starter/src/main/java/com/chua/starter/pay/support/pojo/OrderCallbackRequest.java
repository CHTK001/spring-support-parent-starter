package com.chua.starter.pay.support.pojo;

import com.chua.common.support.utils.StringUtils;
import lombok.Data;

import java.io.Serializable;

/**
 * 订单回调
 * @author CH
 * @since 2024/12/31
 */
@Data
public class OrderCallbackRequest implements Serializable {

    /**
     * 订单号
     */
    private String dataId;
    /**
     * 业务订单号
     */
    private String outTradeId;
    /**
     * 状态
     */
    private Status status;

    /**
     * 支付单号
     */
    private String transactionId;

    /**
     * 错误信息
     */
    private String message;


    public enum Status {

        /**
         * 成功
         */
        SUCCESS,

        /**
         * 失败
         */
        FAILURE
    }
}
