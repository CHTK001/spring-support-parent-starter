package com.chua.payment.support.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包订单视图
 * @author CH
 */
@Data
public class WalletOrderVO {
    /** ID */
    private Long id;
    /** 排序NO */
    private String orderNo;
    /** 排序类型 */
    private String orderType;
    /** MerchantID */
    private Long merchantId;
    /** 用户ID */
    private Long userId;
    /** Related用户ID */
    private Long relatedUserId;
    /** Amount */
    private BigDecimal amount;
    /** 状态 */
    private String status;
    /** Thirdparty排序NO */
    private String thirdPartyOrderNo;
    /** Bankaccount */
    private String bankAccount;
    /** Bank名称 */
    private String bankName;
    /** Account名称 */
    private String accountName;
    /** 请求payload */
    private String requestPayload;
    /** 响应payload */
    private String responsePayload;
    /** Operator */
    private String operator;
    /** Remark */
    private String remark;
    /** NotifyURL */
    private String notifyUrl;
    /** CompletedAT */
    private LocalDateTime completedAt;
    /** Create时间 */
    private LocalDateTime createTime;
    /** Update时间 */
    private LocalDateTime updateTime;
}

