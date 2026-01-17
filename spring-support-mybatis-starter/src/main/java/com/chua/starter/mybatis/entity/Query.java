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
import com.google.common.base.Splitter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * 分页查询基类
 * 提供分页、排序、字段选择等功能
 *
 * @param <T> 实体类型
 * @author CH
 */
@Schema(description = "分页信息")
@Data
@Accessors(chain = true)
public class Query<T> implements Serializable {

    /**
     * 默认页码
     */
    private static final Integer PAGE_NO = 1;

    /**
     * 默认每页条数
     */
    private static final Integer PAGE_SIZE = 10;

    /**
     * 每页条数 - 不分页
     * 例如说，导出接口，可以设置 {@link #pageSize} 为 -1 不分页，查询所有数据。
     */
    public static final Integer PAGE_SIZE_NONE = -1;

    /**
     * 排序方向 - 降序
     */
    private static final String ORDER_DESC = "desc";

    /**
     * 排序方向 - 升序
     */
    private static final String ORDER_ASC = "asc";

    /**
     * 排序方向 - 降序（带空格）
     */
    private static final String ORDER_DESC_WITH_SPACE = " desc";

    /**
     * 排序方向 - 升序（带空格）
     */
    private static final String ORDER_ASC_WITH_SPACE = " asc";

    /**
     * 排序字段 - 创建时间
     */
    private static final String SORT_CREATE_TIME = "createTime";

    /**
     * 排序字段 - 更新时间
     */
    private static final String SORT_UPDATE_TIME = "updateTime";

    /**
     * 排序字段 - 受欢迎度
     */
    private static final String SORT_POPULAR = "popular";

    /**
     * 排序字段 - 最新
     */
    private static final String SORT_NEWEST = "newest";

    /**
     * 排序字段长度 - desc
     */
    private static final int ORDER_DESC_LENGTH = 4;

    /**
     * 排序字段长度 - asc
     */
    private static final int ORDER_ASC_LENGTH = 3;

    @Schema(description = "页码，从 1 开始", requiredMode = Schema.RequiredMode.REQUIRED,example = "1")
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小值为 1")
    @RequestParamMapping({"page", "pageNo", "pageNumber", "pageNum", "current"})
    private Integer page = PAGE_NO;

    @Schema(description = "每页条数，最大值为 100", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小值为 1")
    @Max(value = 100, message = "每页条数最大值为 100")
    @RequestParamMapping({"pageSize", "size", "count"})
    private Integer pageSize = PAGE_SIZE;

    /**
     * 获取每页条数
     *
     * @return 每页条数
     */
    public Integer getPageSize() {
        return pageSize;
    }

    /**
     * 获取页码
     *
     * @return 页码
     */
    public Integer getPage() {
        return page;
    }

    /**
     * 查询字段
     */
    @Schema(description = "查询字段, 多个逗号分隔", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "name, age")
    @Min(value = 1, message = "查询字段, 多个逗号分隔")
    @Max(value = 100, message = "查询字段, 多个逗号分隔")
    @RequestParamMapping({"prop", "field"})
    private String[] prop = new String[0];

    /**
     * 查询字段
     */
    @Schema(description = "排序字段, 多个逗号分隔", requiredMode = Schema.RequiredMode.NOT_REQUIRED, examples = {
            "createTime; 不进行处理",
            "+createTime; e.g asc",
            "-createTime; e.g desc"
    })
    @Min(value = 1, message = "排序字段, 多个逗号分隔")
    @Max(value = 100, message = "排序字段, 多个逗号分隔")
    @RequestParamMapping({"order", "sortBy", "sort", "orderBy"})
    private String order;


    /**
     * 获取查询字段
     *
     * @return 查询字段
     */
    public String getColumns() {
        return ArrayUtils.isEmpty(prop) ? null : String.join(",", prop);
    }

    /**
     * 是否按更新时间排序
     *
     * @return 是否排序
     */
    public boolean isUpdateTime() {
        return sortBy(SORT_UPDATE_TIME);
    }

    /**
     * 是否按创建时间排序
     *
     * @return 是否排序
     */
    public boolean isCreateTime() {
        return sortBy(SORT_CREATE_TIME);
    }

    /**
     * 是否按受欢迎度排序
     *
     * @return 是否排序
     */
    public boolean isPopular() {
        return sortBy(SORT_POPULAR);
    }

    /**
     * 是否按最新排序
     *
     * @return 是否排序
     */
    public boolean isNewest() {
        return sortBy(SORT_NEWEST);
    }

    /**
     * 排序字段
     *
     * @param name 字段名
     * @return 是否排序
     */
    public boolean sortBy(String name) {
        if (StringUtils.isEmpty(order) || StringUtils.isEmpty(name)) {
            return false;
        }
        Set<String> strings = Splitter.on(",").omitEmptyStrings().trimResults()
                .splitToStream(order).map(String::toLowerCase)
                .collect(Collectors.toSet());
        return strings.contains(name.toLowerCase());
    }
    /**
     * 初始化分页（包含排序）
     *
     * @return 分页对象
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> createFullPage() {
        Page<T> tPage = new Page<>(page, pageSize);
        if (StringUtils.isNotEmpty(order)) {
            List<OrderItem> orderItems = parseOrderItems(order);
            tPage.setOrders(orderItems);
        }
        return tPage;
    }

    /**
     * 解析排序字符串为 OrderItem 列表
     *
     * @param orderStr 排序字符串
     * @return OrderItem 列表
     */
    private List<OrderItem> parseOrderItems(String orderStr) {
        String[] orders = orderStr.split(",");
        List<OrderItem> orderItems = new LinkedList<>();
        for (String orderItem : orders) {
            String trimmed = orderItem.trim();
            if (trimmed.endsWith(ORDER_DESC)) {
                String column = trimmed.substring(0, trimmed.length() - ORDER_DESC_LENGTH).trim();
                if (StringUtils.isNotEmpty(column)) {
                    orderItems.add(OrderItem.desc(column));
                }
            } else if (trimmed.endsWith(ORDER_ASC)) {
                String column = trimmed.substring(0, trimmed.length() - ORDER_ASC_LENGTH).trim();
                if (StringUtils.isNotEmpty(column)) {
                    orderItems.add(OrderItem.asc(column));
                }
            }
        }
        return orderItems;
    }
    /**
     * 初始化分页
     *
     * @return {@link com.baomidou.mybatisplus.extension.plugins.pagination.Page}<{@link T}>
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> createPage() {
        return new Page<>(page, pageSize);
    }

    /**
     * 初始化查询包装器
     *
     * @param query 查询包装器
     * @return 配置后的查询包装器
     */
    public QueryWrapper<T> wrapper(QueryWrapper<T> query) {
        applySelect(query);
        applyOrder(query);
        return query;
    }

    /**
     * 创建并初始化查询包装器
     *
     * @return 查询包装器
     */
    public QueryWrapper<T> wrapper() {
        QueryWrapper<T> query = Wrappers.query();
        return wrapper(query);
    }

    /**
     * 应用字段选择
     *
     * @param query 查询包装器
     */
    private void applySelect(QueryWrapper<T> query) {
        if (ArrayUtils.isNotEmpty(prop)) {
            query.select(prop);
        }
    }

    /**
     * 应用排序
     *
     * @param query 查询包装器
     */
    private void applyOrder(QueryWrapper<T> query) {
        if (StringUtils.isEmpty(order)) {
            return;
        }
        String[] orders = order.split(",");
        for (String orderItem : orders) {
            String trimmed = orderItem.toLowerCase().trim();
            if (trimmed.endsWith(ORDER_DESC_WITH_SPACE)) {
                query.orderByDesc(trimmed.replace(ORDER_DESC_WITH_SPACE, ""));
            } else if (trimmed.endsWith(ORDER_ASC_WITH_SPACE)) {
                query.orderByAsc(trimmed.replace(ORDER_ASC_WITH_SPACE, ""));
            }
        }
    }
    /**
     * 创建 MPJ 查询包装器
     *
     * @return MPJ 查询包装器
     */
    public MPJQueryWrapper<T> mpj() {
        MPJQueryWrapper<T> wrapper = new MPJQueryWrapper<>();
        applySelectToMpj(wrapper);
        applyOrderToMpj(wrapper);
        return wrapper;
    }

    /**
     * 应用字段选择到 MPJ 包装器
     *
     * @param wrapper MPJ 查询包装器
     */
    private void applySelectToMpj(MPJQueryWrapper<T> wrapper) {
        if (ArrayUtils.isNotEmpty(prop)) {
            wrapper.select(prop);
        }
    }

    /**
     * 应用排序到 MPJ 包装器
     *
     * @param wrapper MPJ 查询包装器
     */
    private void applyOrderToMpj(MPJQueryWrapper<T> wrapper) {
        if (StringUtils.isEmpty(order)) {
            return;
        }
        String[] orders = order.split(",");
        for (String orderItem : orders) {
            String trimmed = orderItem.toLowerCase().trim();
            if (trimmed.endsWith(ORDER_DESC_WITH_SPACE)) {
                wrapper.orderByDesc(trimmed.replace(ORDER_DESC_WITH_SPACE, ""));
            } else if (trimmed.endsWith(ORDER_ASC_WITH_SPACE)) {
                wrapper.orderByAsc(trimmed.replace(ORDER_ASC_WITH_SPACE, ""));
            }
        }
    }
    /**
     * 创建 Lambda 查询包装器
     *
     * @return Lambda 查询包装器
     */
    public LambdaQueryWrapper<T> lambda() {
        return wrapper().lambda();
    }

    /**
     * 创建 MPJ Lambda 查询包装器
     *
     * @return MPJ Lambda 查询包装器
     */
    public MPJLambdaWrapper<T> mpjLambda() {
        MPJLambdaWrapper<T> wrapper = MPJWrappers.lambdaJoin();
        applySelectToMpjLambda(wrapper);
        applyOrderToMpjLambda(wrapper);
        return wrapper;
    }

    /**
     * 应用字段选择到 MPJ Lambda 包装器
     *
     * @param wrapper MPJ Lambda 查询包装器
     */
    private void applySelectToMpjLambda(MPJLambdaWrapper<T> wrapper) {
        if (ArrayUtils.isNotEmpty(prop)) {
            wrapper.select(prop);
        }
    }

    /**
     * 应用排序到 MPJ Lambda 包装器
     *
     * @param wrapper MPJ Lambda 查询包装器
     */
    private void applyOrderToMpjLambda(MPJLambdaWrapper<T> wrapper) {
        if (StringUtils.isEmpty(order)) {
            return;
        }
        String[] orders = order.split(",");
        for (String orderItem : orders) {
            String trimmed = orderItem.toLowerCase().trim();
            if (trimmed.endsWith(ORDER_DESC_WITH_SPACE)) {
                wrapper.orderByDesc(trimmed.replace(ORDER_DESC_WITH_SPACE, ""));
            } else if (trimmed.endsWith(ORDER_ASC_WITH_SPACE)) {
                wrapper.orderByAsc(trimmed.replace(ORDER_ASC_WITH_SPACE, ""));
            }
        }
    }

    /**
     * 获取偏移量
     *
     * @return 偏移量
     */
    public int offset() {
        return (page - 1) * pageSize;
    }
}
