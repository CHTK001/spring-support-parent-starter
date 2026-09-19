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

    /**
     * 创建record。
     * @param record record
     * @return 创建record的结果
     */
    TransactionRecord createRecord(TransactionRecord record);

    /**
     * 获取byid。
     * @param id 标识
     * @return 获取byid的结果
     */
    TransactionRecord getById(Long id);

    /**
     * 列表by订单no。
     * @param orderNo 订单no
     * @param pageNum pagenum
     * @param pageSize page大小
     * @return 列表by订单no的结果
     */
    Page<TransactionRecord> listByOrderNo(String orderNo, int pageNum, int pageSize);

    /**
     * 列表bymerchantid。
     * @param merchantId merchantid
     * @param pageNum pagenum
     * @param pageSize page大小
     * @return 列表bymerchantid的结果
     */
    Page<TransactionRecord> listByMerchantId(Long merchantId, int pageNum, int pageSize);

    /**
     * page。
     * @param request 请求
     * @return page的结果
     */
    Page<TransactionRecord> page(TransactionRecordQueryRequest request);
}

