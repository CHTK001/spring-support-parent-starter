package com.chua.starter.common.support.utils;

import com.chua.common.support.matcher.PathMatcher;
import com.chua.common.support.core.utils.CollectionUtils;
import com.chua.starter.common.support.constant.OperatorType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * utils工具类
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
public class StreamUtils {
    /**
     * 获取第一个
     *
     * @param data         数据
     * @param idFunction   流名称
     * @return {@link T}
     */
    public static <R, T> Map<R, T> toMap(Collection<T> data, Function<T, R> idFunction) {
        if(null == data || null == idFunction) {
            return Collections.emptyMap();
        }

        if(null == data || null == idFunction) {
            return Collections.emptyMap();
        }

        Map<R, T> rs = new HashMap<>(data.size());
        for (T datum : data) {
            R apply = idFunction.apply(datum);
            if(null == apply) {
                continue;
            }

            rs.put(apply, datum);
        }

        return rs;
    }
    /**
     * 获取第一个
     *
     * @param data         数据
     * @param idFunction   流名称
     * @return {@link T}
     */
    public static <R, T> Map<R, List<T>> tpMultiMap(Collection<T> data, Function<T, R> idFunction) {
        if(null == data || null == idFunction) {
            return Collections.emptyMap();
        }

        Map<R, List<T>> rs = new HashMap<>(data.size());
        for (T datum : data) {
            R apply = idFunction.apply(datum);
            if(null == apply) {
                continue;
            }

            rs.computeIfAbsent(apply, it -> new LinkedList<>()).add(datum);
        }

        return rs;
    }
    /**
     * 获取第一个
     *
     * @param data         数据
     * @param streamName   流名称
     * @param operatorType 运算符类型
     * @param value        值
     * @return {@link T}
     */
    public static <T>T getFirst(Collection<T> data, Function<T, String> streamName, OperatorType operatorType, String value) {
        List<T> ts = get(data, streamName, operatorType, value);
        return CollectionUtils.findFirst(ts);
    }
    /**
     * 获取
     *
     * @param data         数据
     * @param streamName   流名称
     * @param operatorType 运算符类型
     * @param value        值
     * @return {@link T}
     */
    public static <T>List<T> get(Collection<T> data, Function<T, String> streamName, OperatorType operatorType, String value) {
        return CollectionUtils.isEmpty(data) ? null :
                data.stream()
                        .filter(it -> {
                            String name = streamName.apply(it);
                            if(operatorType == OperatorType.LIKE) {
                                return name.contains(value);
                            }

                            if(operatorType == OperatorType.EQ) {
                                return name.equals(value);
                            }

                            if(operatorType == OperatorType.LEFT_LIKE) {
                                return name.startsWith(value);
                            }

                            if(operatorType == OperatorType.RIGHT_LIKE) {
                                return name.endsWith(value);
                            }

                            if(operatorType == OperatorType.REGEX) {
                                return name.matches(value);
                            }

                            if(operatorType == OperatorType.WILL) {
                                return PathMatcher.INSTANCE.match(value, name);
                            }

                            return false;
                        }).collect(Collectors.toList());
    }
}

