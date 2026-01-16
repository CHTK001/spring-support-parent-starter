package com.chua.starter.pay.support.complaints;

import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.starter.pay.support.pojo.SearchComplaintsV2Request;
import com.chua.starter.pay.support.pojo.SearchComplaintsV2Response;

/**
 * 投诉适配器接口
 *
 * @author CH
 * @since 2025-10-15 15:38
 */
@SpiDefault
public class DefaultComplaintsAdaptor implements ComplaintsAdaptor{
    @Override
    public ReturnPageResult<SearchComplaintsV2Response> search(SearchComplaintsV2Request request) {
        return ReturnPageResult.empty();
    }
}
