package com.chua.starter.mybatis.entity;

import com.chua.common.support.database.annotation.ColumnIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * page
 * @author CH
 */
@ApiModel("分页信息")
@Data
public class RequestPage<T>{
    /**
     * 每页显示条数，默认 10
     */
    @ColumnIgnore
    @ApiModelProperty("每页显示条数，默认 10")
    protected long pageSize = 10;

    /**
     * 当前页
     */
    @ColumnIgnore
    @ApiModelProperty("当前页")
    protected long pageNum = 1;

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> createPage() {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<T>(pageNum, pageSize);
    }
}
