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
        if(isMultiKey(key)) {
            List<String> keys = getMultiKey(key);
            wrapper.and(tQueryWrapper -> {
                QueryWrapper<T> tQueryWrapper1 = tQueryWrapper.ne(fields.get(keys.get(0)), value1);
                for (int i = 1; i < keys.size(); i++) {
                    tQueryWrapper1 = tQueryWrapper1.or().ne(fields.get(keys.get(i)), value1);
                }
            });
            return;
        }
        wrapper.ne(fields.get(key), value1);
    }
}
