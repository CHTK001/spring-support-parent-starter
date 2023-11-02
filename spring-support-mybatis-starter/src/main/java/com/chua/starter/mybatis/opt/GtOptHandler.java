package com.chua.starter.mybatis.opt;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.annotations.Spi;

import java.util.Map;

/**
 * gt opt处理程序
 *
 * @author CH
 * @since 2023/10/29
 */
@Spi("gt")
public class GtOptHandler implements OptHandler{
    @Override
    public <T> void doInject(String key, String value1, Map<String, String> fields, QueryWrapper<T> wrapper) {
        if(isMultiKey(key)) {
            wrapper.and(t -> {
                QueryWrapper<T> tQueryWrapper = t;
                for (String s : getMultiKey(key)) {
                    tQueryWrapper = tQueryWrapper.gt(fields.get(s), value1).or();
                }
            });
            return;
        }
        wrapper.gt(key, value1);
    }
}