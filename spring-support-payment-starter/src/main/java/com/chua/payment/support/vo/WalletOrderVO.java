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
    /**
     * 标识
     */
    private Long id;
    /**
     * 订单号
     */
    private String orderNo;
    /**
     * 订单类型
     */
    private String orderType;
    /**
     * 商户ID
     */
    private Long merchantId;
    /**
     * 用户标识
     */
    private Long userId;
    /**
     * 关联用户标识
     */
    private Long relatedUserId;
    /**
     * 金额
     */
    private BigDecimal amount;
    /**
     * 状态
     */
    private String status;
    /**
     * 第三方订单号
     */
    private String thirdPartyOrderNo;
    /**
     * 银行账号
     */
    private String bankAccount;
    /**
     * 银行名称
     */
    private String bankName;
    /**
     * 账户名
     */
    private String accountName;
    /**
     * 请求报文
     */
    private String requestPayload;
    /**
     * 响应报文
     */
    private String responsePayload;
    /**
     * 操作人
     */
    private String operator;
    /**
     * 备注
     */
    private String remark;
    /**
     * 通知URL
     */
    private String notifyUrl;
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
