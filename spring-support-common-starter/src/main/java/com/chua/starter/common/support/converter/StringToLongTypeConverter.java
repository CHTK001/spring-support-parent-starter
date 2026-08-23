package com.chua.starter.common.support.converter;

import com.chua.common.support.utils.DateUtils;
import com.chua.common.support.utils.NumberUtils;
import org.springframework.core.convert.converter.Converter;

import java.text.ParseException;
import java.util.Date;

/**
 * 字符串到长整型（java.lang.Long）的类型转换器。
 *
 * <p>转换优先级如下：
 * <ol>
 *   <li>若字符串为纯数字，则直接通过 {@link NumberUtils#toLong(String)} 转换；</li>
 *   <li>否则尝试将其解析为日期字符串（{@link DateUtils#parseDate(String)}），
 *       并返回其毫秒时间戳；</li>
 *   <li>以上均失败时，返回 {@code null}。</li>
 * </ol>
 *
 * <p>日期解析过程中可能抛出受检异常 {@link ParseException}，
 * 此处捕获后忽略，使 converter 能够安全降级到 {@code null} 返回。</p>
 *
 * @since 2024/9/24
 * @author CH
 */
public class StringToLongTypeConverter implements Converter<String, Long> {

    /**
     * 执行字符串到 Long 的转换。
     *
     * @param source 待转换的字符串
     * @return 转换后的 Long 值；无法转换时返回 null
     */
    @Override
    /** 转换：字符串 -> java.lang.Long */
    public Long convert(String source) {
        // 优先尝试作为数字解析
        if (NumberUtils.isNumber(source)) {
            return NumberUtils.toLong(source);
        }

        // 尝试作为日期解析，获取时间戳
        try {
            Date date = DateUtils.parseDate(source);
            if (date != null) {
                return date.getTime();
            }
        } catch (ParseException e) {
            // 日期解析失败，忽略并继续返回 null
        }

        // 所有解析策略均失败
        return null;
    }
}
