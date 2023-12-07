package com.chua.starter.mybatis.entity;

import com.chua.common.support.datasource.annotation.ColumnIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * page
 * @author CH
 */
@Schema(description ="分页信息")
@Data
public class RequestPage<T>{
    /**
     * 每页显示条数，默认 10
     */
    @ColumnIgnore
    @Schema(description = "每页显示条数，默认 10")
    protected long pageSize = 10;

    /**
     * 当前页
     */
    @ColumnIgnore
    @Schema(description = "当前页")
    protected long page = 1;

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> createPage() {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<T>(page, pageSize);
    }
}
