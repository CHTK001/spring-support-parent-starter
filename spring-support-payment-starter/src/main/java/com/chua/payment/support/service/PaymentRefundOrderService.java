package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.CreateRefundOrderDTO;
import com.chua.payment.support.dto.MarkRefundedDTO;
import com.chua.payment.support.dto.RefundOrderQueryRequest;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.vo.RefundOrderVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退款单服务接口，定义退款单的创建、状态更新与查询操作契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface PaymentRefundOrderService {

    PaymentRefundOrder createRefundOrder(CreateRefundOrderDTO dto);

    PaymentRefundOrder getById(Long id);

    PaymentRefundOrder getByRefundNo(String refundNo);

    PaymentRefundOrder getByThirdPartyRefundNo(String thirdPartyRefundNo);

    RefundOrderVO getDetail(Long id);

    Page<RefundOrderVO> page(RefundOrderQueryRequest request);

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

    PaymentRefundOrder markRefunded(MarkRefundedDTO dto);

    PaymentRefundOrder markFailed(String refundNo,
                                  String responsePayload,
                                  String operator,
                                  String remark);
}

