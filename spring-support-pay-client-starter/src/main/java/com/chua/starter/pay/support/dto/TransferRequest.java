package com.chua.starter.pay.support.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 转账
 * @author CH
 * @since 2025/4/16 9:27
 */
@Data
@Builder
public class TransferRequest {

    /**
     * 商户订单号
     */
    private String outTradeNo;

    /**
     * 转账笔数
     */
    @Builder.Default
    private String transferSceneId = "1000";

    /**
     * 批量转账数量
     */
    @Builder.Default
    private int number = 1;
    /**
     * 转账账号
     */
    private String toUser;

    /**
     * 转账金额
     */
    private BigDecimal amount;

    /**
     * 转账真实姓名
     */
    private String realName;

    /**
     * 转账描述
     */
    private String description;
}
