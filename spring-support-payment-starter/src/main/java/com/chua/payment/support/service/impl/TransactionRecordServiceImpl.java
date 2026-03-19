package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.mapper.TransactionRecordMapper;
import com.chua.payment.support.service.TransactionRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 交易流水服务实现
 */
@Service
@RequiredArgsConstructor
public class TransactionRecordServiceImpl implements TransactionRecordService {
    
    private final TransactionRecordMapper transactionRecordMapper;
    
    @Override
    public TransactionRecord createRecord(TransactionRecord record) {
        transactionRecordMapper.insert(record);
        return record;
    }
    
    @Override
    public TransactionRecord getById(Long id) {
        return transactionRecordMapper.selectById(id);
    }
    
    @Override
    public Page<TransactionRecord> listByOrderNo(String orderNo, int pageNum, int pageSize) {
        Page<TransactionRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TransactionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransactionRecord::getOrderNo, orderNo)
               .orderByDesc(TransactionRecord::getCreateTime);
        return transactionRecordMapper.selectPage(page, wrapper);
    }
    
    @Override
    public Page<TransactionRecord> listByMerchantId(Long merchantId, int pageNum, int pageSize) {
        Page<TransactionRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TransactionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransactionRecord::getMerchantId, merchantId)
               .orderByDesc(TransactionRecord::getCreateTime);
        return transactionRecordMapper.selectPage(page, wrapper);
    }
    
    @Override
    public Page<TransactionRecord> page(int pageNum, int pageSize) {
        Page<TransactionRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TransactionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(TransactionRecord::getCreateTime);
        return transactionRecordMapper.selectPage(page, wrapper);
    }
}
