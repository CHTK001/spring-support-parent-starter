package com.chua.starter.mybatis.opt;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.annotations.Spi;

import java.util.List;
import java.util.Map;

/**
 * le opt处理程序
 *
 * @author CH
 * @since 2023/10/29
 */
@Spi("le")
public class LeOptHandler implements OptHandler{
    @Override
    public <T> void doInject(String key, String value1, Map<String, String> fields, QueryWrapper<T> wrapper) {
        wrapper.le(fields.get(key), value1);
    }
}
