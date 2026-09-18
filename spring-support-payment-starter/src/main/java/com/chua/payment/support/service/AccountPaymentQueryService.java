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

    /**
    * 列表订单by用户。
    * @param request 请求
    * @return 列表订单by用户的结果
    */
    Page<OrderVO> listOrdersByUser(UserOrderQueryRequest request);

    /**
    * 获取订单by用户。
    * @param userId 用户标识
    * @param id 标识
    * @return 获取订单by用户的结果
    */
    OrderVO getOrderByUser(Long userId, Long id);

    /**
    * 列表订单日志by用户。
    * @param userId 用户标识
    * @param id 标识
    * @return 列表订单日志by用户的结果
    */
    List<OrderStateLogVO> listOrderLogsByUser(Long userId, Long id);

    /**
    * 列表transactionsby用户。
    * @param request 请求
    * @return 列表transactionsby用户的结果
    */
    Page<TransactionRecord> listTransactionsByUser(UserTransactionQueryRequest request);
}

