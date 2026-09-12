package com.chua.starter.common.support.converter;

import com.chua.common.support.utils.DateUtils;
import org.springframework.core.convert.converter.Converter;

import java.util.Date;

/**
* str -> 日期
*
* @since 2022/8/11 9:14
* @author CH
 */
public class StringToDateTypeConverter implements Converter<String, Date> {
    @Override
    /** 转换 */
    public Date convert(String source) {
        try {
        return DateUtils.parseDate(source);
        } catch (Exception e) {
            return null;
        }
    }

}

