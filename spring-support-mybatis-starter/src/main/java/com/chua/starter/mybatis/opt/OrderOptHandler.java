package com.chua.starter.mybatis.opt;


import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.Ascii;
import com.chua.common.support.utils.StringUtils;

import java.util.List;
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
        if(DESC.equalsIgnoreCase(value1)) {
            wrapper.orderByDesc(fields.get(key));
            return;
        }
        wrapper.orderByAsc(fields.get(key));
    }
}
