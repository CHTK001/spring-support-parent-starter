package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.base.bean.BeanUtils;
import com.chua.common.support.core.utils.CollectionUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.entity.Sorting;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MyBatis 工具类
 * 提供分页构建、数据拷贝等工具方法
 *
 * @author CH
 */
public class MybatisUtils {

    /**
     * MySQL 转义字符
     */
    private static final String MYSQL_ESCAPE_CHARACTER = "`";

    /**
     * 构建分页对象
     *
     * @param pageParam 分页参数
     * @param <T>       实体类型
     * @return 分页对象
     */
    public static <T> Page<T> buildPage(Query pageParam) {
        return buildPage(pageParam, null);
    }

    /**
     * 构建分页对象（包含排序）
     *
     * @param pageParam     分页参数
     * @param sortingFields 排序字段集合
     * @param <T>           实体类型
     * @return 分页对象
     */
    public static <T> Page<T> buildPage(Query pageParam, Collection<Sorting> sortingFields) {
        Page<T> page = new Page<>(pageParam.getPage(), pageParam.getPageSize());
        if (!CollectionUtils.isEmpty(sortingFields)) {
            List<OrderItem> orderItems = sortingFields.stream()
                    .map(sortingField -> Sorting.ORDER_ASC.equals(sortingField.getOrder())
                            ? OrderItem.asc(sortingField.getField())
                            : OrderItem.desc(sortingField.getField()))
                    .collect(Collectors.toList());
            page.addOrder(orderItems);
        }
        return page;
    }

    /**
     * 分页数据拷贝
     * 将源分页对象的数据拷贝到目标类型的分页对象中
     *
     * @param result 源分页对象
     * @param type   目标类型
     * @param <T>    目标类型
     * @return 目标类型的分页对象
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
