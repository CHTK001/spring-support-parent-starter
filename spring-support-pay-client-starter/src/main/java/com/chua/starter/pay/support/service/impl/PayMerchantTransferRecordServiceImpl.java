package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.pojo.CreateTransferV2Response;
import com.chua.starter.pay.support.pojo.CreateTransferV2Request;
import com.chua.starter.pay.support.postprocessor.PayCreateOrderPostprocessor;
import com.chua.starter.pay.support.postprocessor.PayTransferPostprocessor;
import com.chua.starter.pay.support.preprocess.PayTransferPreprocess;
import com.chua.starter.pay.support.transfer.TransferBillsAdaptor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.mapper.PayMerchantTransferRecordMapper;
import com.chua.starter.pay.support.entity.PayMerchantTransferRecord;
import com.chua.starter.pay.support.service.PayMerchantTransferRecordService;
/**
 *
 * @author CH
 * @since 2025/10/15 10:17
 */
@Service
public class PayMerchantTransferRecordServiceImpl extends ServiceImpl<PayMerchantTransferRecordMapper, PayMerchantTransferRecord> implements PayMerchantTransferRecordService{

    @Override
    public ReturnResult<CreateTransferV2Response> transfer(CreateTransferV2Request request) {
        //创建订单预处理 -> 支持SPI优先级覆盖
        PayTransferPreprocess payTransferPreprocess = PayTransferPreprocess.createProcessor();
        ReturnResult<CreateTransferV2Request> preprocess = payTransferPreprocess.preprocess(request);
        if (preprocess.isFailure()) {
            return ReturnResult.illegal(preprocess.getMsg());
        }
        //以预处理的结果作为依据, 预处理可能讲原始数据ID转成后端数据库中的金额, 防止前端参数异常
        request = preprocess.getData();
        TransferBillsAdaptor transferBillsAdaptor = ServiceProvider.of(TransferBillsAdaptor.class).getNewExtension(request.getPayTransfer());
        if (null == transferBillsAdaptor) {
            return ReturnResult.illegal("请选择正确的转账类型");
        }

        return transferBillsAdaptor.createOrder(request);
    }

    @Override
    public PayMerchantTransferRecord getByCode(String transferBillNo) {
        return this.getOne(Wrappers.<PayMerchantTransferRecord>lambdaQuery().eq(PayMerchantTransferRecord::getPayMerchantTransferRecordCode, transferBillNo));
    }
}
