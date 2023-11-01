package com.chua.starter.mybatis.opt;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.annotations.Spi;

import java.util.List;
import java.util.Map;

/**
 * ne opt处理程序
 *
 * @author CH
 * @since 2023/10/29
 */
@Spi("ne")
public class NeOptHandler implements OptHandler{
    @Override
    public <T> void doInject(String key, String value1, Map<String, String> fields, QueryWrapper<T> wrapper) {
        wrapper.ne(fields.get(key), value1);
    }
}
