package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.vo.OrderVO;

/**
 * 支付订单服务接口
 *
 * @author CH
 * @since 2026-03-18
 */
public interface PaymentOrderService {

    /**
     * 创建订单
     */
    OrderVO createOrder(OrderCreateDTO dto);

    /**
     * 查询订单
     */
    OrderVO getOrder(Long id);

    /**
     * 分页查询订单
     */
    Page<OrderVO> listOrders(int page, int size, String orderNo, Integer status);

    /**
     * 更新订单状态
     */
    boolean updateOrderStatus(Long id, Integer status, String operator);

    /**
     * 取消订单
     */
    boolean cancelOrder(Long id, String operator);

    /**
     * 完成订单
     */
    boolean completeOrder(Long id, String operator);

    /**
     * 申请退款
     */
    boolean refundOrder(Long id, String reason, String operator);

    /**
     * 查询退款
     */
    OrderVO getRefundOrder(Long id);

    /**
     * 订单超时自动取消
     */
    void autoCancelTimeoutOrders();

    /**
     * 生成订单号
     */
    String generateOrderNo();
}
