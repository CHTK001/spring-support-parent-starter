package com.chua.starter.pay.support.service;

import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户钱包服务接口
 *
 * @author CH
 * @since 2025-10-14
 */
public interface PayUserWalletService extends IService<PayUserWallet> {

    /**
     * 根据用户ID获取用户钱包信息
     *
     * @param userId 用户ID，不能为空，例如："user_123456"
     * @return 用户钱包信息，如果未找到则返回null
     * @throws RuntimeException 当数据库查询异常时抛出
     */
    PayUserWallet getByUser(String userId);

    /**
     * 更新用户钱包信息
     *
     * @param userId            用户ID，不能为空，例如："user_123456"
     * @param payMerchantOrder  商户订单信息，不能为空
     * @return 成功失败
     * @throws RuntimeException 当数据库更新异常时抛出
     */
    boolean updateWallet(String userId, PayMerchantOrder payMerchantOrder);
}
