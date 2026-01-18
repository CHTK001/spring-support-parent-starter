package com.chua.starter.pay.support.tradebill;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.TradebillV2Request;
import com.chua.starter.pay.support.pojo.TradebillV2Response;

/**
 * 交易账单适配器接口
 *
 * @author CH
 * @since 2025/10/15 16:10
 */
public interface TradebillAdaptor {

    /**
     * 下载交易账单
     *
     * @param request 交易账单请求参数 {@link TradebillV2Request}
     * @return 返回账单下载结果，包含账单文件路径或下载链接
     */
    ReturnResult<TradebillV2Response> download(TradebillV2Request request);
}
