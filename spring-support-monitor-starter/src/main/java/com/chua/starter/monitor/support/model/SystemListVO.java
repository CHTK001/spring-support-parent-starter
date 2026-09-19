package com.chua.starter.monitor.support.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统管理列表分页视图。
 *
 * <p>进程、服务等通道只能整表取回，分页在管控端完成，因此对外统一为
 * {@code {list,total}} 结构而非通用 {@code PageResult}，以保持既有前端契约。</p>
 *
 * @param <T> 行类型
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class SystemListVO<T> {

    /**
     * 当前页数据行
     */
    private List<T> list = new ArrayList<>();

    /**
     * 过滤后的总条数
     */
    private long total;

    /**
     * 构造分页列表。
     *
     * @param rows  当前页数据行
     * @param total 总条数
     * @param <T>   行类型
     * @return 分页视图
     */
    public static <T> SystemListVO<T> of(List<T> rows, long total) {
        SystemListVO<T> vo = new SystemListVO<>();
        vo.setList(rows == null ? new ArrayList<>() : rows);
        vo.setTotal(total);
        return vo;
    }
}
