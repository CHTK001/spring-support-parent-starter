package com.chua.starter.common.support.jackson;

import com.chua.common.support.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;

/**
 * 通用本地日期时间解析
 *
 * @since 2024/12/19
 * @author CH
 */
public class CommonLocalDateTimeDeserializer extends LocalDateTimeDeserializer {

    /**
     * 日志
     */
    private static final System.Logger LOG = System.getLogger(CommonLocalDateTimeDeserializer.class.getName());

    @Override
    /**
     * 反序列化
     * @param context 参数
     * @param parser 参数
     * @return 返回值
     * @throws IOException 异常
    */
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        IOException ex;
        try {
            return super.deserialize(parser, context);
        } catch (IOException e) {
            ex = e;
        }

        if (parser.hasTokenId(JsonTokenId.ID_STRING)) {
            try {
                return DateUtils.toLocalDateTime(parser.getText());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.DEBUG, "[jackson] 解析 LocalDateTime 失败，回退原始异常", e);
            }
        }
        throw ex;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt,
                                                BeanProperty property) throws JsonMappingException {
        return this;
    }

}

