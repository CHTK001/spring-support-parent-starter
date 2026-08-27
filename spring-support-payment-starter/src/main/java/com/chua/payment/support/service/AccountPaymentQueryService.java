package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.UserOrderQueryRequest;
import com.chua.payment.support.dto.UserTransactionQueryRequest;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;

import java.util.List;

/**
 * 账户支付查询服务接口，定义用户维度订单、交易流水与状态日志查询契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface AccountPaymentQueryService {

    Page<OrderVO> listOrdersByUser(UserOrderQueryRequest request);

    OrderVO getOrderByUser(Long userId, Long id);

    List<OrderStateLogVO> listOrderLogsByUser(Long userId, Long id);

    Page<TransactionRecord> listTransactionsByUser(UserTransactionQueryRequest request);
}

