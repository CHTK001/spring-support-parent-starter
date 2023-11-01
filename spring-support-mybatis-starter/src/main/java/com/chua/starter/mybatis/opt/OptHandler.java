package com.chua.starter.mybatis.opt;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.function.Splitter;

import java.util.List;
import java.util.Map;

import static com.chua.common.support.constant.CommonConstant.SYMBOL_COMMA;

/**
 * opt处理程序
 *
 * @author CH
 * @since 2023/10/29
 */
public interface OptHandler {

    /**
     * 是多键
     *
     * @param key 钥匙
     * @return boolean
     */
    default boolean isMultiKey(String key) {
        return key.contains(SYMBOL_COMMA);
    }

    /**
     * 获取多钥匙
     *
     * @param key 钥匙
     * @return {@link List}<{@link String}>
     */
    default List<String> getMultiKey(String key) {
        return  Splitter.on(SYMBOL_COMMA).omitEmptyStrings().trimResults().splitToList(key);
    }

    /**
     * 确实注入
     *
     * @param key     钥匙
     * @param value1  值1
     * @param fields  字段
     * @param wrapper 包装物
     */
    <T> void doInject(String key, String value1, Map<String, String> fields, QueryWrapper<T> wrapper);
}
