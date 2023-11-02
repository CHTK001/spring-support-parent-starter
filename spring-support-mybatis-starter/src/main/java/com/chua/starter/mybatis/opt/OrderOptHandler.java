package com.chua.starter.mybatis.opt;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.annotations.Spi;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * order opt处理程序
 *
 * @author CH
 * @since 2023/10/29
 */
@Spi("order")
public class OrderOptHandler implements OptHandler{
    private static final String DESC = "desc";

    @Override
    public <T> void doInject(String key, String value1, Map<String, String> fields, QueryWrapper<T> wrapper) {
        if(isMultiKey(key)) {
            if(DESC.equalsIgnoreCase(value1)) {
                wrapper.orderByDesc(getMultiKey(key).stream().map(fields::get).collect(Collectors.toList()));
                return;
            }
            wrapper.orderByAsc(getMultiKey(key).stream().map(fields::get).collect(Collectors.toList()));
            return;
        }
        if(DESC.equalsIgnoreCase(value1)) {
            wrapper.orderByDesc(key);
            return;
        }
        wrapper.orderByAsc(key);
    }
}
