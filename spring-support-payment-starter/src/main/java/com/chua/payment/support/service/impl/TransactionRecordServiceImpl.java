package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.TransactionRecordQueryRequest;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.mapper.TransactionRecordMapper;
import com.chua.payment.support.service.TransactionRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
* 交易流水服务实现，提供支付、退款、充值、转账等交易记录的分页查询。
*
* @author CH
* @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
public class TransactionRecordServiceImpl implements TransactionRecordService {

    /** Transactionrecordmapper */
    private final TransactionRecordMapper transactionRecordMapper;

    @Override
    /** 创建Record */
    public TransactionRecord createRecord(TransactionRecord record) {
        transactionRecordMapper.insert(record);
        return record;
    }

    @Override
    /** 获取byid */
    public TransactionRecord getById(Long id) {
        return transactionRecordMapper.selectById(id);
    }

    @Override
    public Page<TransactionRecord> listByOrderNo(String orderNo, int pageNum, int pageSize) {
        return page(new TransactionRecordQueryRequest(pageNum, pageSize, null, orderNo, null, null));
    }

    @Override
    public Page<TransactionRecord> listByMerchantId(Long merchantId, int pageNum, int pageSize) {
        return page(new TransactionRecordQueryRequest(pageNum, pageSize, merchantId, null, null, null));
    }

    @Override
    public Page<TransactionRecord> page(TransactionRecordQueryRequest request) {
        if (request == null) {
            /**
    * illegal参数异常。
    * @param "查询参数不能为空" "查询参数不能为空"
    * @return illegal参数异常的结果
    */
            throw new IllegalArgumentException("查询参数不能为空");
        }
        Page<TransactionRecord> page = new Page<>(request.pageNum(), request.pageSize());
        LambdaQueryWrapper<TransactionRecord> wrapper = new LambdaQueryWrapper<>();
        if (request.merchantId() != null) {
            wrapper.eq(TransactionRecord::getMerchantId, request.merchantId());
        }
        if (StringUtils.hasText(request.orderNo())) {
            wrapper.eq(TransactionRecord::getOrderNo, request.orderNo());
        }
        if (StringUtils.hasText(request.transactionType())) {
            wrapper.eq(TransactionRecord::getTransactionType, request.transactionType());
        }
        if (request.status() != null) {
            wrapper.eq(TransactionRecord::getStatus, request.status());
        }
        wrapper.orderByDesc(TransactionRecord::getCreatedAt);
        return transactionRecordMapper.selectPage(page, wrapper);
    }
}

