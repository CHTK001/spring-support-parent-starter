package com.chua.starter.common.support.utils;

import java.util.StringJoiner;

import static com.chua.common.support.constant.CommonConstant.EMPTY;

/**
 * 数组工具类
 *
 * @author CH
 * @since 2025/6/6 19:39
 */
public class ArrayUtils {

    /**
     * 连接数组
     *
     * @param array     数组
     * @param separator 分隔符
     * @return
     */
    public static String join(Object[] array, String separator) {
        if (array == null) {
            return null;
        }
        StringJoiner stringJoiner = new StringJoiner(separator);
        for (Object o : array) {
            if (null == o) {
                stringJoiner.add(EMPTY);
                continue;
            }
            if (o instanceof byte[] bytes) {
                stringJoiner.add(new String(bytes));
                continue;
            }
            stringJoiner.add(o.toString());
        }
        return stringJoiner.toString();
    }
}
