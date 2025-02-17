package com.chua.starter.mybatis.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页条件
 *
 * @author CH
 */
public class PagePreconditioning<T> {
    private final IPage<T> page;

    public PagePreconditioning(IPage<T> page) {
        this.page = page;
    }

    /**
     * 转换
     *
     * @param function 转化器
     */
    public <R> PagePreconditioning<R> convert(Function<T, R> function) {
        IPage<R> page = new Page<>(this.page.getCurrent(), this.page.getSize(), this.page.getTotal());
        page.setRecords(this.page.getRecords().stream().map(function).collect(Collectors.toList()));
        return new PagePreconditioning<R>(page);
    }

    /**
     * 映射
     *
     * @param idFunction 映射
     * @param consumer   消费
     */
    public PagePreconditioning<T> mapping(Function<T, Serializable> idFunction, BiConsumer<Set<Serializable>, Map<Serializable, T>> consumer) {
        List<T> records = page.getRecords();
        Map<Serializable, T> map = records.stream().collect(Collectors.toMap(idFunction, Function.identity()));
        consumer.accept(map.keySet(), map);
        return this;
    }

    /**
     * 返回分页
     *
     * @return 分页
     */
    public IPage<T> returnPage() {
        return page;
    }
}
