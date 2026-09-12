package com.chua.payment.support.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
* 钱包订单视图
* @author CH
* @since 4.0.0
 */
@Data
public class WalletOrderVO {
    /** 标识 */
    private Long id;
    /** 排序NO */
    private String orderNo;
    /** 排序类型 */
    private String orderType;
    /** merchantid */
    private Long merchantId;
    /** 用户标识 */
    private Long userId;
    /** Related用户标识 */
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
    /** 账户名称 */
    private String accountName;
    /** 请求payload */
    private String requestPayload;
    /** 响应payload */
    private String responsePayload;
    /** 操作符 */
    private String operator;
    /** 评论 */
    private String remark;
    /** 通知url */
    private String notifyUrl;
    /** 完成at */
    private LocalDateTime completedAt;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}

