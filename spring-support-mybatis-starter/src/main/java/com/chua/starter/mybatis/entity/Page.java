package com.chua.starter.mybatis.entity;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * page
 * @author CH
 */
@ApiModel("分页信息")
@Data
@Accessors(chain = true)
public class Page<T> implements IPage<T> {

    /**
     * 查询数据列表
     */
    protected List<T> records = Collections.emptyList();

    /**
     * 总数
     */
    protected long total = 0;
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

    /**
     * 排序字段信息
     */
    @Setter
    protected List<OrderItem> orders = new ArrayList<>();

    /**
     * 自动优化 COUNT SQL
     */
    protected boolean optimizeCountSql = true;
    /**
     * 是否进行 count 查询
     */
    protected boolean searchCount = true;
    /**
     * {@link #optimizeJoinOfCountSql()}
     */
    @Setter
    protected boolean optimizeJoinOfCountSql = true;
    /**
     * 单页分页条数限制
     */
    @Setter
    protected Long maxLimit;
    /**
     * countId
     */
    @Setter
    protected String countId;

    @Override
    public List<OrderItem> orders() {
        return orders;
    }

}
