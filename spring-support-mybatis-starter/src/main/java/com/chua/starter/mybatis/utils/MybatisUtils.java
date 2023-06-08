package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.bean.BeanUtils;

import java.util.List;

/**
 * tools
 *
 * @author CH
 */
public class MybatisUtils {

    /**
     * 分页数据拷贝
     *
     * @param result 数据
     * @param type   类型
     * @param <T>    类型
     * @return 结果
     */
    public static <T> Page<T> copy(Page<?> result, Class<T> type) {
        Page<T> page = new Page<>();
        BeanUtils.copyProperties(result, page);

        List<?> records = result.getRecords();
        List<T> ts = BeanUtils.copyPropertiesList(records, type);
        page.setRecords(ts);
        return page;
    }
}
