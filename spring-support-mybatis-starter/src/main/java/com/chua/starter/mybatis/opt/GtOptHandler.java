package com.chua.starter.mybatis.opt;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.annotations.Spi;

import java.util.List;
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
            List<String> keys = getMultiKey(key);
            wrapper.and(tQueryWrapper -> {
                QueryWrapper<T> tQueryWrapper1 = tQueryWrapper.gt(fields.get(keys.get(0)), value1);
                for (int i = 1; i < keys.size(); i++) {
                    tQueryWrapper1 = tQueryWrapper1.or().gt(fields.get(keys.get(i)), value1);
                }
            });
            return;
        }
        wrapper.gt(fields.get(key), value1);
    }
}
