package com.chua.starter.pay.support.service;

import com.chua.starter.pay.support.entity.PayMerchantFailureRecord;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author CH
 * @since 2025/10/15 9:50
 */
public interface PayMerchantFailureRecordService extends IService<PayMerchantFailureRecord> {


    /**
     * 保存记录
     * @param record 记录
     */
    void saveRecord(PayMerchantFailureRecord record);
}
