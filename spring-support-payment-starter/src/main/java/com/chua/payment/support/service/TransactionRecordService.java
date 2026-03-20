package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.entity.TransactionRecord;

/**
 * 交易流水服务接口
 */
public interface TransactionRecordService {

    TransactionRecord createRecord(TransactionRecord record);

    TransactionRecord getById(Long id);

    Page<TransactionRecord> listByOrderNo(String orderNo, int pageNum, int pageSize);

    Page<TransactionRecord> listByMerchantId(Long merchantId, int pageNum, int pageSize);

    Page<TransactionRecord> page(int pageNum, int pageSize, Long merchantId, String orderNo, String transactionType, Integer status);
}
