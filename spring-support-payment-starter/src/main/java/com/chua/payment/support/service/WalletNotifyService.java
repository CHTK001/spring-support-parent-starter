package com.chua.payment.support.service;

import com.chua.payment.support.dto.WalletNotifyDTO;

/**
* 钱包异步回调处理服务接口，定义钱包订单通知处理的契约。
*
* @author CH
* @since 4.0.0.50
 */
public interface WalletNotifyService {

    /**
    * 把手通知。
    * @param dto dto
     */
    void handleNotify(WalletNotifyDTO dto);
}

