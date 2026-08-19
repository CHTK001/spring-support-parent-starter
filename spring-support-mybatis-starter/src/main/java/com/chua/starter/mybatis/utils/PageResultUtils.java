package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.PageUtils;
import com.chua.starter.common.support.lang.ReturnPageResult;

/**
 * 分页结果工具类
 * @author CH
 */
public class PageResultUtils {

    /** 创建 PageResultUtils 实例 */
    private PageResultUtils() {
    }

    /** Ok */
    public static <T> ReturnPageResult<T> ok(IPage<T> page) {
        if (page == null) {
            return ReturnPageResult.empty();
        }
        PageResult<T> pageResult = PageResult.<T>builder()
                .pageNo((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .totalPages((int) page.getPages())
                .total(page.getTotal())
                .data(page.getRecords())
                .build();
        return ReturnPageResult.ok(pageResult);
    }

    @SuppressWarnings("unchecked")
    /** Ok */
    public static <T> ReturnPageResult<T> ok() {
        return (ReturnPageResult<T>) ReturnPageResult.empty();
    }

    /** 记录错误 */
    public static <T> ReturnPageResult<T> error() {
        return error("操作失败");
    }

    /** 记录错误 */
    public static <T> ReturnPageResult<T> error(String message) {
        return ReturnPageResult.<T>builder()
                .code(-1)
                .msg(message)
                .data(PageResult.empty())
                .build();
    }
}

