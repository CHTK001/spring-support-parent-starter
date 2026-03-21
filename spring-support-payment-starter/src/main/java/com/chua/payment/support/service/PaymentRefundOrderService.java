package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.vo.RefundOrderVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退款单服务
 */
public interface PaymentRefundOrderService {

    PaymentRefundOrder createRefundOrder(PaymentOrder order,
                                         String refundNo,
                                         BigDecimal refundAmount,
                                         String reason,
                                         String operator,
                                         String requestPayload);

    PaymentRefundOrder getById(Long id);

    PaymentRefundOrder getByRefundNo(String refundNo);

    RefundOrderVO getDetail(Long id);

    Page<RefundOrderVO> page(int pageNum, int pageSize, Long merchantId, String orderNo, String refundNo, String status);

    PaymentRefundOrder getLatestByOrderId(Long orderId);

    PaymentRefundOrder getProcessingByOrderId(Long orderId);

    List<PaymentRefundOrder> listByOrderId(Long orderId);

    List<RefundOrderVO> listVoByOrderId(Long orderId);

    boolean hasProcessingRefund(Long orderId);

    BigDecimal sumRefundedAmount(Long orderId);

    PaymentRefundOrder markProcessing(String refundNo,
                                      String thirdPartyRefundNo,
                                      String responsePayload,
                                      String remark);

    PaymentRefundOrder markRefunded(String refundNo,
                                    BigDecimal refundAmount,
                                    String thirdPartyRefundNo,
                                    String responsePayload,
                                    String operator,
                                    String remark);

    PaymentRefundOrder markFailed(String refundNo,
                                  String responsePayload,
                                  String operator,
                                  String remark);
}
