package com.chua.starter.pay.support.tradebill;

import com.chua.common.support.core.annotation.SpiDefault;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.TradebillV2Request;
import com.chua.starter.pay.support.pojo.TradebillV2Response;

/**
 * 默认交易账单适配器接口
 * @author CH
 * @since 2025/10/15 16:18
 */
@SpiDefault
public class DefaultTradebillAdaptor implements TradebillAdaptor {
    @Override
    public ReturnResult<TradebillV2Response> download(TradebillV2Request request) {
        return ReturnResult.ok();
    }
}
