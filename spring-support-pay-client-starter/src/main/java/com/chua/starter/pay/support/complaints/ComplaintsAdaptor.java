package com.chua.starter.pay.support.complaints;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.starter.pay.support.pojo.SearchComplaintsV2Request;
import com.chua.starter.pay.support.pojo.SearchComplaintsV2Response;

/**
 * 投诉适配器接口
 *
 * @author CH
 * @since 2025-10-15 15:38
 */
public interface ComplaintsAdaptor {

    /**
     * 查询投诉信息
     *
     * @param request 查询投诉请求参数
     * @return 投诉查询结果响应
     */
    ReturnPageResult<SearchComplaintsV2Response> search(SearchComplaintsV2Request request);
}
