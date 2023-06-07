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


    public static <T> Page<T> copy(Page<?> dictItemPage, Class<T> dictPageVOClass) {
        Page<T> page = new Page<>();
        BeanUtils.copyProperties(dictItemPage, page);

        List<?> records = dictItemPage.getRecords();
        List<T> ts = BeanUtils.copyPropertiesList(records, dictPageVOClass);
        page.setRecords(ts);
        return page;
    }
}
