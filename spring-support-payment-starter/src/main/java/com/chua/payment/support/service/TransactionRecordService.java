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
     * 创建交易流水记录。
     * @param record 流水记录
     * @return 创建出的流水记录
     */
    TransactionRecord createRecord(TransactionRecord record);

    /**
     * 根据主键获取流水记录。
     * @param id 标识
     * @return 获取到的流水记录
     */
    TransactionRecord getById(Long id);

    /**
     * 按订单号分页查询流水。
     * @param orderNo 订单号
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 按订单号查询的流水分页结果
     */
    Page<TransactionRecord> listByOrderNo(String orderNo, int pageNum, int pageSize);

    /**
     * 按商户标识分页查询流水。
     * @param merchantId 商户标识
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 按商户标识查询的流水分页结果
     */
    Page<TransactionRecord> listByMerchantId(Long merchantId, int pageNum, int pageSize);

    /**
     * 分页查询流水记录。
     * @param request 请求参数
     */
    Page<TransactionRecord> page(TransactionRecordQueryRequest request);
}
