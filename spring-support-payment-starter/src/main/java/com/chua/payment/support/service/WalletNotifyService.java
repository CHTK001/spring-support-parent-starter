package com.chua.payment.support.service;

import com.chua.payment.support.dto.WalletNotifyDTO;

/**
 * 钱包异步回调处理服务接口，定义钱包订单通知处理契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WalletNotifyService {

    /**
     * 处理钱包通知。
     * @param dto dto
     */
    void handleNotify(WalletNotifyDTO dto);
}
