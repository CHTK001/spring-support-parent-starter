package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantTransferRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.pay.support.pojo.CreateTransferV2Response;
import com.chua.starter.pay.support.pojo.CreateTransferV2Request;

/**
 * 商户转账记录服务接口
 *
 * @author CH
 * @since 2025-10-15
 */
public interface PayMerchantTransferRecordService extends IService<PayMerchantTransferRecord> {

    /**
     * 转账操作
     *
     * @param request 转账请求参数 {@link CreateTransferV2Request}
     * @return 转账结果 {@link CreateTransferV2Response}
     */
    ReturnResult<CreateTransferV2Response> transfer(CreateTransferV2Request request);

    /**
     * 根据订单号查询
     *
     * @param transferBillNo 订单号
     * @return 商户转账记录
     */
    PayMerchantTransferRecord getByCode(String transferBillNo);
}
