package com.chua.payment.support.service;

import com.chua.payment.support.vo.PaymentDashboardSummaryVO;

import java.time.LocalDate;

/**
 * 支付首页业务统计服务。
 */
public interface PaymentDashboardService {

    PaymentDashboardSummaryVO summary(LocalDate startDate, LocalDate endDate);
}
