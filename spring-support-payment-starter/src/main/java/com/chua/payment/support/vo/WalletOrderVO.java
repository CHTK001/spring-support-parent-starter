package com.chua.payment.support.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包订单视图
 */
@Data
public class WalletOrderVO {
    private Long id;
    private String orderNo;
    private String orderType;
    private Long merchantId;
    private Long userId;
    private Long relatedUserId;
    private BigDecimal amount;
    private String status;
    private String thirdPartyOrderNo;
    private String bankAccount;
    private String bankName;
    private String accountName;
    private String requestPayload;
    private String responsePayload;
    private String operator;
    private String remark;
    private String notifyUrl;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
