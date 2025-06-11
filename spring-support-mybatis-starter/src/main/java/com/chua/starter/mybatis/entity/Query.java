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
 * page 查询
 * @author CH
 */
@Schema(description ="分页信息")
@Data
@Accessors(chain = true)
public class Query<T> implements Serializable {
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
    @RequestParamMapping({"page", "pageNo", "pageNumber", "pageNum", "current"})
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
     * 是否最近更新
     *
     * @return 是否排序
     */
    public boolean isUpdateTime() {
        return sortBy("updateTime");
    }

    /**
     * 是否最新添加
     *
     * @return 是否排序
     */
    public boolean isCreateTime() {
        return sortBy("createTime");
    }

    /**
     * 是否最受欢迎
     *
     * @return 是否排序
     */
    public boolean isPopular() {
        return sortBy("popular");
    }

    /**
     * 是否最受欢迎
     *
     * @return 是否排序
     */
    public boolean isNewest() {
        return sortBy("newest");
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
     * 初始化分页
     *
     * @return {@link com.baomidou.mybatisplus.extension.plugins.pagination.Page}<{@link T}>
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> createFullPage() {
        Page<T> tPage = new Page<>(page, pageSize);
        if (null != order) {
            String[] orders = order.split(",");
            List<OrderItem> orderItem = new LinkedList<>();
            for (String s : orders) {
                if (s.endsWith("desc")) {
                    orderItem.add(OrderItem.desc(s.substring(0, s.length() - 4).trim()));
                } else if (s.endsWith("asc")) {
                    orderItem.add(OrderItem.asc(s.substring(0, s.length() - 3).trim()));
                }
            }
            tPage.setOrders(orderItem);
        }

        return tPage;
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
     * 初始化其它参数
     *
     * @return {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}<{@link T}>
     */
    public QueryWrapper<T> wrapper(QueryWrapper<T> query) {
        if(ArrayUtils.isNotEmpty(prop)) {
           query.select(prop);
        }

        if (null != order) {
            for (String s : order.split(",")) {
                s = s.toLowerCase();
                if (s.endsWith(" desc")) {
                    query.orderByDesc(s.replace(" desc", ""));
                } else if (s.endsWith(" asc")) {
                    query.orderByAsc(s.replace(" asc", ""));
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

        if (null != order) {
            for (String s : order.split(",")) {
                s = s.toLowerCase();
                if (s.endsWith(" desc")) {
                    wrapper.orderByDesc(s.replace(" desc", ""));
                } else if (s.endsWith(" asc")) {
                    wrapper.orderByAsc(s.replace(" asc", ""));
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

        if (null != order) {
            for (String s : order.split(",")) {
                s = s.toLowerCase();
                if (s.endsWith(" desc")) {
                    wrapper.orderByDesc(s.replace(" desc", ""));
                } else if (s.endsWith(" asc")) {
                    wrapper.orderByAsc(s.replace(" asc", ""));
                }
            }
        }
        return wrapper;
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
