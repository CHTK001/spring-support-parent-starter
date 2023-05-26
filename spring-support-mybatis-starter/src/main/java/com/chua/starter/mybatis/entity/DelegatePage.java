package com.chua.starter.mybatis.entity;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.database.annotation.ColumnIgnore;
import com.chua.common.support.utils.CollectionUtils;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Collections;
import java.util.List;

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
    protected long pageNo = 1;

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> createPage() {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<T>(pageNo, pageSize);
    }
}
