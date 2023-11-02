package com.chua.starter.mybatis.opt;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.function.Splitter;

import java.util.Map;

/**
 * notin opt处理程序
 *
 * @author CH
 * @since 2023/10/29
 */
@Spi("NOT_IN")
public class NotInOptHandler implements OptHandler{
    @Override
    public <T> void doInject(String key, String value1, Map<String, String> fields, QueryWrapper<T> wrapper) {
        if(isMultiKey(key)) {
            wrapper.and(t -> {
                QueryWrapper<T> tQueryWrapper = t;
                for (String s : getMultiKey(key)) {
                    tQueryWrapper = tQueryWrapper.notIn(fields.get(s), Splitter.on(',').omitEmptyStrings().trimResults().splitToSet(value1)).or();
                }
            });
            return;
        }
        wrapper.notIn(key, Splitter.on(',').omitEmptyStrings().trimResults().splitToSet(value1));
    }
}
