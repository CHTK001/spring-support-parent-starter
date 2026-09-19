package com.chua.starter.common.support.jackson;

import com.chua.common.support.utils.DateUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.datatype.jsr310.deser.YearDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Year;

/**
 * Year反序列化
 *
 * <p>该反序列化器用于将 JSON 中的年份字段（如 {@code "2024"} 或
 * {@code "2024-01-01 12:00:00"}）反序列化为 {@link Year} 对象。
 *
 * <p>解析策略：
 * <ol>
 *   <li>首先尝试标准的字符串解析（父类 {@link YearDeserializer} 默认行为）；</li>
 *   <li>若标准解析抛出 {@link IOException}，则尝试通过
 *       {@link DateUtils#toLocalDateTime(String)} 解析为
 *       {@link LocalDateTime}，再从中提取年份；</li>
 *   <li>日期解析可能抛出受检异常 {@link java.text.ParseException}，
 *       此处捕获并忽略，避免中断反序列化流程。</li>
 * </ol>
 *
 * @since 2024/12/19
 * @author CH
 */
public class CommonYearDeserializer extends YearDeserializer {

    /**
     * 将 JSON 解析为 Year 对象。
     *
     * @param parser  JSON 解析器
     * @param context 反序列化上下文
     * @return 解析出的 Year 实例
     * @throws IOException 反序列化过程中无法恢复的 IO 错误
     */
    @Override
    /**
     * 反序列化
    */
    public Year deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            try {
                // 尝试标准解析
                return _fromString(parser, context, parser.getText());
            } catch (IOException e) {
 // 标准解析失败，尝试通过 日期工具 解析为 本地日期时间
                try {
                    LocalDateTime ldt = DateUtils.toLocalDateTime(parser.getText());
                    if (ldt != null) {
                        return Year.from(ldt);
                    }
                } catch (java.text.ParseException ignored) {
                    // 日期字符串解析失败，忽略并继续
                }
                throw e;
            }
        }
 // 30-Sep-2020, tatu: 新! "Scalar 从 对象" (Most.js for XML)
        if (t == JsonToken.START_OBJECT) {
            return _fromString(parser, context,
                    context.extractScalarFromObject(parser, this, handledType()));
        }
        if (t == JsonToken.VALUE_NUMBER_INT) {
            return _fromNumber(context, parser.getIntValue());
        }
        if (t == JsonToken.VALUE_EMBEDDED_OBJECT) {
            return (Year) parser.getEmbeddedObject();
        }
        if (parser.hasToken(JsonToken.START_ARRAY)) {
            return _deserializeFromArray(parser, context);
        }
        return _handleUnexpectedToken(context, parser, JsonToken.VALUE_STRING, JsonToken.VALUE_NUMBER_INT);
    }

    /**
     * 创建上下文感知的反序列化器实例。
     *
     * @param ctxt     反序列化上下文
     * @param property Bean 属性
     * @return 当前反序列化器实例
     * @throws JsonMappingException 无法创建上下文实例时抛出
     */
    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt,
                                                BeanProperty property) throws JsonMappingException {
        return this;
    }
}
