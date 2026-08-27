package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.TransactionRecordQueryRequest;
import com.chua.payment.support.entity.TransactionRecord;

/**
 * 交易流水服务接口，定义流水记录的创建与分页查询契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface TransactionRecordService {

    TransactionRecord createRecord(TransactionRecord record);

    TransactionRecord getById(Long id);

    Page<TransactionRecord> listByOrderNo(String orderNo, int pageNum, int pageSize);

    Page<TransactionRecord> listByMerchantId(Long merchantId, int pageNum, int pageSize);

    Page<TransactionRecord> page(TransactionRecordQueryRequest request);
}

