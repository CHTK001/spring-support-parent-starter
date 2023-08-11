package com.chua.starter.common.support.request;

import com.chua.starter.common.support.annotations.RequestParamMapping;
import lombok.Data;

/**
 * 分页请求
 * @author CH
 */
@Data
public class PageRequest<T> {
    /**
     * 当前页
     */
    @RequestParamMapping({"page", "pageNum"})
    private Integer pageNum = 1;

    /**
     * 限制数量
     */
    @RequestParamMapping({"pageSize", "size"})
    private Integer pageSize = 10;
}
