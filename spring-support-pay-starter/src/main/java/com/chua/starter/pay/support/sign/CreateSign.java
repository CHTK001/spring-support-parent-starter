package com.chua.starter.pay.support.sign;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.value.Value;
import com.chua.starter.pay.support.emuns.TradeType;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.PayConfigDetector;
import com.chua.starter.pay.support.handler.PaySignCreator;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.PaySignCreateRequest;
import com.chua.starter.pay.support.result.PaySignResponse;
import com.chua.starter.pay.support.service.PayMerchantService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 创建签名
 * @author CH
 * @since 2024/12/31
 */
public class CreateSign {
    private final TransactionTemplate transactionTemplate;
    private final PayMerchantService payMerchantService;
    private final PayMerchantOrderMapper payMerchantOrderMapper;

    public CreateSign(TransactionTemplate transactionTemplate, PayMerchantService payMerchantMapper, PayMerchantOrderMapper payMerchantOrderMapper) {
        this.transactionTemplate = transactionTemplate;
        this.payMerchantService = payMerchantMapper;
        this.payMerchantOrderMapper = payMerchantOrderMapper;
    }

    public ReturnResult<PaySignResponse> create(PaySignCreateRequest request, PayMerchantOrder payMerchantOrder) {
        String merchantCode = payMerchantOrder.getPayMerchantCode();
        ReturnResult<PayMerchant> payMerchantValue = payMerchantService.getOneByCode(merchantCode);
        if(null == payMerchantValue || !payMerchantValue.isOk()) {
            return ReturnResult.error("商户不存在");
        }

        PayMerchant payMerchant = payMerchantValue.getData();
        String tradeType = payMerchantOrder.getPayMerchantOrderTradeType().toUpperCase();
        PayConfigDetector<?> payConfigDetector = ServiceProvider.of(PayConfigDetector.class).getNewExtension(tradeType);
        if(null == payConfigDetector) {
            return ReturnResult.illegal("当前系统不支持该下单方式");
        }

        ReturnResult<?> checked = payConfigDetector.check(payMerchant, TradeType.valueOf(tradeType));
        if(!checked.isOk()) {
            return ReturnResult.illegal(checked.getMsg());
        }
        PaySignCreator paySignCreator = ServiceProvider.of(PaySignCreator.class).getNewExtension(tradeType, checked.getData());
        ReturnResult<PaySignResponse> handle = paySignCreator.handle(request);
        if(!handle.isOk()) {
            return handle;
        }
        PaySignResponse paySignResponse = handle.getData();
        return ReturnResult.success(paySignResponse);
    }
}
