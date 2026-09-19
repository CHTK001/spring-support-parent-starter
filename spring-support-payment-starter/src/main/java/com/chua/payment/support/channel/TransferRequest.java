package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 转账请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class TransferRequest {
    /**
     * 调动no
    */
    private String transferNo;
    /**
     * 通知url
    */
    private String notifyUrl;
    /**
     * merchantid
    */
    private Long merchantId;
    /**
     * 从用户标识
    */
    private Long fromUserId;
    /**
     * 转为用户标识
    */
    private Long toUserId;
    /**
     * Amount
    */
    private BigDecimal amount;
    /**
     * 操作符
    */
    private String operator;
    /**
     * 评论
    */
    private String remark;
}

