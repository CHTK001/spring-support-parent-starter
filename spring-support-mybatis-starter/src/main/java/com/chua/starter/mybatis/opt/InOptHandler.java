package com.chua.starter.mybatis.opt;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.utils.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * in opt处理程序
 *
 * @author CH
 * @since 2023/10/29
 */
@Spi("in")
public class InOptHandler implements OptHandler{
    @Override
    public <T> void doInject(String key, String value1, Map<String, String> fields, QueryWrapper<T> wrapper) {
        wrapper.in(fields.get(key), Splitter.on(',').omitEmptyStrings().trimResults().splitToSet(value1));
    }
}
