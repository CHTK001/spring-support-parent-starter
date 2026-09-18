package com.chua.payment.support.service;

import com.chua.payment.support.vo.PaymentDashboardSummaryVO;

import java.time.LocalDate;

/**
* 支付首页业务统计服务接口，定义汇总数据查询契约。
*
* @author CH
* @since 4.0.0.50
 */
public interface PaymentDashboardService {

    /**
    * summary。
    * @param startDate 启动日期
    * @param endDate 结束日期
    * @return summary的结果
    */
    PaymentDashboardSummaryVO summary(LocalDate startDate, LocalDate endDate);
}

