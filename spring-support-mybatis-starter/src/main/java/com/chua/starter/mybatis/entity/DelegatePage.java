package com.chua.starter.mybatis.entity;

import com.chua.common.support.database.annotation.ColumnIgnore;
import lombok.Data;

/**
 * page
 * @author CH
 */
@Data
public class DelegatePage<T>{
    /**
     * 每页显示条数，默认 10
     */
    @ColumnIgnore
    protected long pageSize = 10;

    /**
     * 当前页
     */
    @ColumnIgnore
    protected long pageNo = 0;

    /**
     * 当前页
     */
    @ColumnIgnore
    protected long page = 0;

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> createPage() {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<T>(page < 0 ? Math.max(pageNo, 1) : page, pageSize);
    }
}
