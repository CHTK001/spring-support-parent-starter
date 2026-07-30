package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;

import java.util.List;

public interface AccountPaymentQueryService {

    Page<OrderVO> listOrdersByUser(Long userId, int page, int size, String orderNo, String status);

    OrderVO getOrderByUser(Long userId, Long id);

    List<OrderStateLogVO> listOrderLogsByUser(Long userId, Long id);

    Page<TransactionRecord> listTransactionsByUser(Long userId, int pageNum, int pageSize, String orderNo, String transactionType, Integer status);
}
