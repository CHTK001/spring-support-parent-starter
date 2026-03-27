package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 钱包订单模拟回调 DTO
 */
@Data
public class WalletOrderNotifyDTO {

    private String status;

    private String thirdPartyOrderNo;

    private String payload;

    private String reason;
}
