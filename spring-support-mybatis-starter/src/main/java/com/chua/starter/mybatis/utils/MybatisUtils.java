package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.entity.Sorting;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * tools
 *
 * @author CH
 */
public class MybatisUtils {
    private static final String MYSQL_ESCAPE_CHARACTER = "`";

    /**
     * 构建分页
     *
     * @param pageParam 分页参数
     * @return {@link Page}<{@link T}>
     */
    public static <T> Page<T> buildPage(Query pageParam) {
        return buildPage(pageParam, null);
    }

    /**
     * 构建分页
     *
     * @param pageParam     分页参数
     * @param sortingFields 排序字段
     * @return {@link Page}<{@link T}>
     */
    public static <T> Page<T> buildPage(Query pageParam, Collection<Sorting> sortingFields) {
        // 页码 + 数量
        Page<T> page = new Page<>(pageParam.getPage(), pageParam.getPageSize());
        // 排序字段
        if (!CollectionUtils.isEmpty(sortingFields)) {
            page.addOrder(sortingFields.stream().map(sortingField -> Sorting.ORDER_ASC.equals(sortingField.getOrder()) ?
                            OrderItem.asc(sortingField.getField()) : OrderItem.desc(sortingField.getField()))
                    .collect(Collectors.toList()));
        }
        return page;
    }

    /**
     * 分页数据拷贝
     *
     * @param result 数据
     * @param type   类型
     * @param <T>    类型
     * @return 结果
     */
    public static <T> Page<T> copy(Page<?> result, Class<T> type) {
        Page<T> page = new Page<>();
        BeanUtils.copyProperties(result, page);

        List<?> records = result.getRecords();
        List<T> ts = BeanUtils.copyPropertiesList(records, type);
        page.setRecords(ts);
        return page;
    }
}
