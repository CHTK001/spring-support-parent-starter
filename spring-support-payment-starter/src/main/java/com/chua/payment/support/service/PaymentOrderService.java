package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.dto.OrderPayDTO;
import com.chua.payment.support.dto.RefundApplyDTO;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 支付订单服务接口
 */
public interface PaymentOrderService {

    OrderVO createOrder(OrderCreateDTO dto);

    OrderVO getOrder(Long id);

    Page<OrderVO> listOrders(int page, int size, Long merchantId, String orderNo, String status);

    Page<OrderVO> listMerchantOrders(Long merchantId, int page, int size, String status);

    List<OrderStateLogVO> listOrderLogs(Long id);

    PaymentResult payOrder(Long id, OrderPayDTO dto);

    OrderVO syncOrder(Long id);

    boolean startPay(Long id, String operator);

    boolean paySuccess(Long id, BigDecimal paidAmount, String thirdPartyOrderNo, String operator);

    boolean payFail(Long id, String reason, String operator);

    boolean cancelOrder(Long id, String operator, String reason);

    boolean completeOrder(Long id, String operator);

    boolean refundOrder(Long id, RefundApplyDTO dto);

    boolean refundSuccess(Long id, BigDecimal refundAmount, String operator);

    boolean refundSuccess(String refundNo,
                          BigDecimal refundAmount,
                          String thirdPartyRefundNo,
                          String operator,
                          String responsePayload,
                          String remark);

    boolean refundFail(Long id, String reason, String operator);

    boolean refundFail(String refundNo, String responsePayload, String operator, String reason);

    boolean deleteOrder(Long id);

    String generateOrderNo();

    void autoCancelTimeoutOrders();
}
