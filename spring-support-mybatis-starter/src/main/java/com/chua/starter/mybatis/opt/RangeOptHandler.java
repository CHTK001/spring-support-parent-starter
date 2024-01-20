package com.chua.starter.mybatis.opt;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonArray;
import com.chua.common.support.utils.StringUtils;

import java.util.Map;

/**
 * range opt处理程序
 *
 * @author CH
 * @since 2023/10/29
 */
@Spi("range")
public class RangeOptHandler implements OptHandler{
    @Override
    public <T> void doInject(String key, String value1, Map<String, String> fields, QueryWrapper<T> wrapper) {
        JsonArray jsonArray = Json.getJsonArray(value1);
        if(isMultiKey(key)) {
            for (String s : getMultiKey(key)) {
                wrapper.between(StringUtils.isNotBlank(value1),
                        fields.get(s), jsonArray.get(0), jsonArray.get(1));
            }
            return;
        }
        wrapper.between(StringUtils.isNotBlank(value1),
                key, jsonArray.get(0), jsonArray.get(1));
    }
}
