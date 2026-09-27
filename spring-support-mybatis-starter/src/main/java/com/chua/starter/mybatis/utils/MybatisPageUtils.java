package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.BeanUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * MyBatis-Plus 分页工具类，统一处理分页对象与分页数据之间的转换。
 *
 * <p>通用分页计算仍由 {@code com.chua.common.support.utils.PageUtils} 提供；
 * 本类只负责 MyBatis-Plus 专属的 {@link IPage} 适配。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class MybatisPageUtils {

    /**
     * 工具类禁止实例化。
     */
    private MybatisPageUtils() {
    }

    /**
     * 将列表转换为单页分页对象。
     *
     * @param data 分页数据，允许为空
     * @param page 当前页码，必须大于零
     * @param size 每页数量，必须大于零
     * @param <T> 数据类型
     * @return 单页分页对象，记录数和总记录数与输入列表一致
     * @throws IllegalArgumentException 当页码或每页数量不合法时抛出
     */
    public static <T> IPage<T> page(List<T> data, int page, int size) {
        if (page <= 0) {
            throw new IllegalArgumentException("页码必须大于零");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("每页数量必须大于零");
        }
        List<T> records = data == null ? List.of() : data;
        Page<T> pageResult = new Page<>(page, size);
        pageResult.setRecords(records);
        pageResult.setTotal(records.size());
        return pageResult;
    }

    /**
     * 复制分页元数据并转换每一条记录。
     *
     * @param page 源分页对象，不能为空
     * @param type 目标记录工厂，必须返回非空对象
     * @param <E> 源记录类型
     * @param <R> 目标记录类型
     * @return 目标分页对象
     * @throws IllegalArgumentException 当源分页对象或目标记录工厂为空时抛出
     */
    public static <E, R> IPage<R> copyProperties(IPage<E> page, Supplier<R> type) {
        Objects.requireNonNull(page, "page 不能为空");
        Objects.requireNonNull(type, "type 不能为空");
        List<E> records = page.getRecords() == null ? List.of() : page.getRecords();
        List<R> copiedRecords = records.stream()
                .map(item -> {
                    R target = Objects.requireNonNull(type.get(), "目标记录不能为空");
                    BeanUtils.copyProperties(item, target);
                    return target;
                })
                .toList();
        return copyPage(page, copiedRecords);
    }

    /**
     * 复制分页元数据并将每一条记录转换为指定类型。
     *
     * @param page 源分页对象，不能为空
     * @param type 目标记录类型，不能为空
     * @param <E> 源记录类型
     * @param <R> 目标记录类型
     * @return 目标分页对象
     * @throws IllegalArgumentException 当源分页对象或目标记录类型为空时抛出
     */
    public static <E, R> IPage<R> copyProperties(IPage<E> page, Class<R> type) {
        Objects.requireNonNull(type, "type 不能为空");
        Object emptySource = null;
        return copyProperties(page, () -> BeanUtils.copyProperties(emptySource, type));
    }

    /**
     * 复制分页元数据和已转换的记录列表。
     *
     * @param page 源分页对象，不能为空
     * @param records 已转换的记录列表，不能为空
     * @param <E> 源记录类型
     * @param <R> 目标记录类型
     * @return 目标分页对象
     */
    private static <E, R> IPage<R> copyPage(IPage<E> page, List<R> records) {
        IPage<R> result = new Page<>();
        result.setPages(page.getPages());
        result.setTotal(page.getTotal());
        result.setSize(page.getSize());
        result.setCurrent(page.getCurrent());
        result.setRecords(records);
        return result;
    }
}
