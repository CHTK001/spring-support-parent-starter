package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.UserOrderQueryRequest;
import com.chua.payment.support.dto.UserTransactionQueryRequest;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;

import java.util.List;

/**
 * 账户支付查询服务接口，定义以用户为维度的订单、交易流水与状态日志查询封装契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface AccountPaymentQueryService {

    /**
     * 按用户分页查询订单列表。
     * @param request 请求参数
     * @return 按用户分页查询的订单分页结果
     */
    Page<OrderVO> listOrdersByUser(UserOrderQueryRequest request);

    /**
     * 按用户查询单个订单详情。
     * @param userId 用户标识
     * @param id 标识
     * @return 按用户查询到的订单详情
     */
    OrderVO getOrderByUser(Long userId, Long id);

    /**
     * 按用户查询订单状态日志列表。
     * @param userId 用户标识
     * @param id 标识
     * @return 按用户查询的订单状态日志列表
     */
    List<OrderStateLogVO> listOrderLogsByUser(Long userId, Long id);

    /**
     * 按用户分页查询交易流水列表。
     * @param request 请求参数
     * @return 按用户分页查询的交易流水分页结果
     */
    Page<TransactionRecord> listTransactionsByUser(UserTransactionQueryRequest request);
}
