package com.chua.starter.ai.support.service;

import java.math.BigDecimal;

/**
 * AI 余额服务接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface AiBalanceService {

    /**
     * 检查分组余额是否充足。
     *
     * @param groupId 分组 ID
     * @return 是否充足
     */
    boolean checkBalance(Integer groupId);

    /**
     * 扣减分组余额。
     *
     * @param groupId 分组 ID
     * @param amount  扣减金额
     */
    void deduct(Integer groupId, BigDecimal amount);

    /**
     * 获取分组可用余额。
     *
     * @param groupId 分组 ID
     * @return 可用余额
     */
    BigDecimal getAvailableBalance(Integer groupId);

    /**
     * 扣减分组余额。
     *
     * @param groupId 分组 ID
     * @param amount  扣减金额
     */
    void deductBalance(Integer groupId, BigDecimal amount);

    /**
     * 获取用户余额。
     *
     * @param groupId 分组 ID
     * @return 用户余额
     */
    BigDecimal getUserBalance(Integer groupId);
}
