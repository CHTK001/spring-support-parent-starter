package com.chua.starter.mybatis.entity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.ArrayUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.common.support.annotations.RequestParamMapping;
import com.github.yulichang.query.MPJQueryWrapper;
import com.github.yulichang.toolkit.MPJWrappers;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;


/**
 * page 查询
 * @author CH
 */
@Schema(description ="分页信息")
@Data
public class Query<T>{
    private static final Integer PAGE_NO = 1;
    private static final Integer PAGE_SIZE = 10;

    /**
     * 每页条数 - 不分页
     *
     * 例如说，导出接口，可以设置 {@link #pageSize} 为 -1 不分页，查询所有数据。
     */
    public static final Integer PAGE_SIZE_NONE = -1;

    @Schema(description = "页码，从 1 开始", requiredMode = Schema.RequiredMode.REQUIRED,example = "1")
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小值为 1")
    @RequestParamMapping({"page", "pageNo", "current"})
    private Integer page = PAGE_NO;

    @Schema(description = "每页条数，最大值为 100", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小值为 1")
    @Max(value = 100, message = "每页条数最大值为 100")
    @RequestParamMapping({"pageSize", "size", "count"})
    private Integer pageSize = PAGE_SIZE;

    /**
     * 查询字段
     */
    @Schema(description = "查询字段", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 1, message = "查询字段")
    @Max(value = 100, message = "查询字段")
    @RequestParamMapping({"prop", "field"})
    private String[] prop = new String[0];

    /**
     * 查询字段
     */
    @Schema(description = "排序字段", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 1, message = "排序字段")
    @Max(value = 100, message = "排序字段")
    @RequestParamMapping({"order"})
    private String[] order = new String[0];

    /**
     * 初始化分页
     *
     * @return {@link com.baomidou.mybatisplus.extension.plugins.pagination.Page}<{@link T}>
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> createPage() {
        Page<T> tPage = new Page<>(page, pageSize);
        if(ArrayUtils.isNotEmpty(order)) {
            List<OrderItem> orderItem = new LinkedList<>();
            for (String s : order) {
                if(s.endsWith("-")) {
                    orderItem.add(OrderItem.desc(s.substring(0, s.length() - 1)));
                } else if(s.endsWith("+")){
                    orderItem.add(OrderItem.asc(s.substring(0, s.length() - 1)));
                } else {
                    orderItem.add(OrderItem.asc(s));
                }
            }
            tPage.setOrders(orderItem);
        }

        return tPage;
    }

    /**
     * 初始化其它参数
     *
     * @return {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}<{@link T}>
     */
    public QueryWrapper<T> wrapper(QueryWrapper<T> query) {
        if(ArrayUtils.isNotEmpty(prop)) {
           query.select(prop);
        }

        if(ArrayUtils.isNotEmpty(order)) {
            for (String s : order) {
                if(s.endsWith("-")) {
                    query.orderByDesc(s.substring(0, s.length() - 1));
                } else if(s.endsWith("+")){
                    query.orderByAsc(s.substring(0, s.length() - 1));
                } else {
                    query.orderByAsc(s);
                }
            }
        }
        return query;
    }
    public QueryWrapper<T> wrapper() {
        QueryWrapper<T> query = Wrappers.query();
        return wrapper(query);
    }
    /**
     * 初始化其它参数
     *
     * @return {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}<{@link T}>
     */
    public MPJQueryWrapper<T> mpj() {
        MPJQueryWrapper<T> wrapper = new MPJQueryWrapper<>();
        if(ArrayUtils.isNotEmpty(prop)) {
            wrapper.select(prop);
        }

        if(ArrayUtils.isNotEmpty(order)) {
            for (String s : order) {
                if(s.endsWith("-")) {
                    wrapper.orderByDesc(s.substring(0, s.length() - 1));
                } else if(s.endsWith("+")){
                    wrapper.orderByAsc(s.substring(0, s.length() - 1));
                } else {
                    wrapper.orderByAsc(s);
                }
            }
        }
        return wrapper;
    }
    /**
     * 初始化其它参数
     *
     * @return {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}<{@link T}>
     */
    public LambdaQueryWrapper<T> lambda() {
        return wrapper().lambda();
    }
    /**
     * 初始化其它参数
     *
     * @return {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}<{@link T}>
     */
    public MPJLambdaWrapper<T> mpjLambda() {
        MPJLambdaWrapper<T> wrapper = MPJWrappers.lambdaJoin();

        if(ArrayUtils.isNotEmpty(prop)) {
            wrapper.select(prop);
        }

        if(ArrayUtils.isNotEmpty(order)) {
            for (String s : order) {
                if(s.endsWith("-")) {
                    wrapper.orderByDesc(s.substring(0, s.length() - 1));
                } else if(s.endsWith("+")){
                    wrapper.orderByAsc(s.substring(0, s.length() - 1));
                } else {
                    wrapper.orderByAsc(s);
                }
            }
        }
        return wrapper;
    }
}
