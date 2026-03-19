package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.entity.TransactionRecord;

/**
 * 交易流水服务接口
 */
public interface TransactionRecordService {
    
    /**
     * 创建流水记录
     */
    TransactionRecord createRecord(TransactionRecord record);
    
    /**
     * 根据ID查询流水
     */
    TransactionRecord getById(Long id);
    
    /**
     * 根据订单号查询流水列表
     */
    Page<TransactionRecord> listByOrderNo(String orderNo, int pageNum, int pageSize);
    
    /**
     * 根据商户ID查询流水列表
     */
    Page<TransactionRecord> listByMerchantId(Long merchantId, int pageNum, int pageSize);
    
    /**
     * 分页查询流水列表
     */
    Page<TransactionRecord> page(int pageNum, int pageSize);
}
