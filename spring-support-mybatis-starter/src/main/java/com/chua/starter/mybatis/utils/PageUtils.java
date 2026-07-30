package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;

import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 分页工具
 * @author CH
 * @since 2024/12/31
 */
public class PageUtils {

    private PageUtils() {
    }

    /**
     * 分页复制
     * @param page 分页
     * @param type 类型
     * @param <E> E 原始类型
     * @param <R> R 返回类型
     * @return IPage
     */
    public static <E, R>IPage<R> copyProperties(IPage<E> page, Supplier<R> type) {
        IPage<R> rs = new Page<>();
        rs.setPages(page.getPages());
        rs.setTotal(page.getTotal());
        rs.setSize(page.getSize());
        rs.setCurrent(page.getCurrent());
        rs.setRecords(page.getRecords().stream().map(it -> {
            try {
                R r = type.get();
                BeanUtils.copyProperties(it, r);
                return r;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList()));
        return rs;
    }

    /**
     * 分页复制
     * @param page 分页
     * @param type 类型
     * @param <E> E 原始类型
     * @param <R> R 返回类型
     * @return IPage
     */
    public static <E, R>IPage<R> copyProperties(IPage<E> page, Class<R> type) {
        return copyProperties(page, () -> {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
