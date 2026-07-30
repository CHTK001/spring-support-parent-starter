package com.chua.starter.ai.support.service;

import java.math.BigDecimal;

public interface AiBalanceService {

    boolean checkBalance(Integer groupId);

    void deduct(Integer groupId, BigDecimal amount);

    BigDecimal getAvailableBalance(Integer groupId);

    void deductBalance(Integer groupId, BigDecimal amount);

    BigDecimal getUserBalance(Integer groupId);
}
