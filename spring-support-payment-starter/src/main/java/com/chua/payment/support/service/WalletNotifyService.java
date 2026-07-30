package com.chua.payment.support.service;

/**
 * 钱包异步回调处理服务
 */
public interface WalletNotifyService {

    void handleNotify(String orderType,
                      String orderNo,
                      String thirdPartyOrderNo,
                      String status,
                      String payload,
                      String reason);
}
