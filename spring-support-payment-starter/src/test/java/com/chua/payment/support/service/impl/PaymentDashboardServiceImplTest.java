package com.chua.payment.support.service.impl;

import com.chua.payment.support.entity.PaymentNotifyLog;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.PaymentNotifyLogMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.mapper.PaymentRefundOrderMapper;
import com.chua.payment.support.mapper.TransactionRecordMapper;
import com.chua.payment.support.vo.PaymentDashboardSummaryVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentDashboardServiceImplTest {

    private final MerchantMapper merchantMapper = mock(MerchantMapper.class);
    private final MerchantChannelMapper merchantChannelMapper = mock(MerchantChannelMapper.class);
    private final PaymentOrderMapper paymentOrderMapper = mock(PaymentOrderMapper.class);
    private final PaymentRefundOrderMapper paymentRefundOrderMapper = mock(PaymentRefundOrderMapper.class);
    private final PaymentNotifyLogMapper paymentNotifyLogMapper = mock(PaymentNotifyLogMapper.class);
    private final TransactionRecordMapper transactionRecordMapper = mock(TransactionRecordMapper.class);

    private final PaymentDashboardServiceImpl service = new PaymentDashboardServiceImpl(
            merchantMapper,
            merchantChannelMapper,
            paymentOrderMapper,
            paymentRefundOrderMapper,
            paymentNotifyLogMapper,
            transactionRecordMapper
    );

    @Test
    void shouldAggregateBusinessSummary() {
        PaymentOrder order = new PaymentOrder();
        order.setPaidAmount(new BigDecimal("88.80"));
        order.setOrderAmount(new BigDecimal("99.90"));

        PaymentRefundOrder refundOrder = new PaymentRefundOrder();
        refundOrder.setRefundAmount(new BigDecimal("12.50"));

        PaymentNotifyLog notifyLog1 = new PaymentNotifyLog();
        notifyLog1.setProcessStatus("SUCCESS");
        notifyLog1.setReceivedTime(LocalDateTime.of(2026, 4, 12, 10, 0, 0));
        notifyLog1.setProcessedTime(LocalDateTime.of(2026, 4, 12, 10, 0, 1));

        PaymentNotifyLog notifyLog2 = new PaymentNotifyLog();
        notifyLog2.setProcessStatus("FAILED");
        notifyLog2.setReceivedTime(LocalDateTime.of(2026, 4, 12, 11, 0, 0));
        notifyLog2.setProcessedTime(LocalDateTime.of(2026, 4, 12, 11, 0, 3));

        when(merchantMapper.selectCount(any())).thenReturn(8L, 5L);
        when(merchantChannelMapper.selectObjs(any())).thenReturn(List.of(1L, 2L, 3L));
        when(paymentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(paymentRefundOrderMapper.selectList(any())).thenReturn(List.of(refundOrder));
        when(transactionRecordMapper.selectCount(any())).thenReturn(13L);
        when(paymentNotifyLogMapper.selectList(any())).thenReturn(List.of(notifyLog1, notifyLog2));

        PaymentDashboardSummaryVO summary = service.summary(LocalDate.of(2026, 4, 12), LocalDate.of(2026, 4, 12));

        assertEquals(8L, summary.getMerchantTotal());
        assertEquals(5L, summary.getActiveMerchantCount());
        assertEquals(3L, summary.getConfiguredMerchantCount());
        assertEquals(1L, summary.getPaymentOrderCount());
        assertEquals(new BigDecimal("88.80"), summary.getTotalConsumeAmount());
        assertEquals(1L, summary.getRefundCount());
        assertEquals(new BigDecimal("12.50"), summary.getTotalRefundAmount());
        assertEquals(13L, summary.getTransactionCount());
        assertEquals(2L, summary.getCallbackRequestCount());
        assertEquals(1L, summary.getSuccessNotifyCount());
        assertEquals(1L, summary.getFailedNotifyCount());
        assertEquals(2000L, summary.getAverageProcessDurationMs());
    }
}
