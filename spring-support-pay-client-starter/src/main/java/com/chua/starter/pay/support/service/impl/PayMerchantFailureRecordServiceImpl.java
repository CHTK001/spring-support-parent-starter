package com.chua.starter.pay.support.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.mapper.PayMerchantFailureRecordMapper;
import com.chua.starter.pay.support.entity.PayMerchantFailureRecord;
import com.chua.starter.pay.support.service.PayMerchantFailureRecordService;
/**
 *
 * @author CH
 * @since 2025/10/15 9:50
 */
@Service
public class PayMerchantFailureRecordServiceImpl extends ServiceImpl<PayMerchantFailureRecordMapper, PayMerchantFailureRecord> implements PayMerchantFailureRecordService{

    @Override
    public void saveRecord(PayMerchantFailureRecord record) {
        Thread.ofVirtual().start(() -> save(record));
    }
}
