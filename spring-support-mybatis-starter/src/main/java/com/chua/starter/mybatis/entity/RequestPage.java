package com.chua.starter.mybatis.entity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * page
 * @author CH
 */
@ApiModel("分页信息")
@Data
@Accessors(chain = true)
public class RequestPage<T>{
    /**
     * 每页显示条数，默认 10
     */
    @ApiModelProperty("每页显示条数，默认 10")
    protected long size = 10;

    /**
     * 当前页
     */
    @ApiModelProperty("当前页")
    protected long current = 1;

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> getPage() {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<T>(current, size);
    }
}
