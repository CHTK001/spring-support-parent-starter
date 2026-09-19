package com.chua.starter.mybatis.entity;

import com.chua.common.support.annotation.ColumnIgnore;
import lombok.Data;

/**
 * page
 * @author CH
 * @since 4.0.0
 */
@Data
public class DelegatePage<T>{
    /**
     * 每页显示条数，默认 10
     */
    @ColumnIgnore
    /** 每页大小 */
    protected long pageSize = 10;

    /**
    * 当前页
    */
    @ColumnIgnore
    /** 页NO */
    protected long pageNo = 0;

    /**
    * 当前页
    */
    @ColumnIgnore
    /** 页 */
    protected long page = 0;

    /**
    * 创建Page
    *
    * @return 创建page的结果
    */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> createPage() {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<T>(page < 0 ? Math.max(pageNo, 1) : page, pageSize);
    }
}
