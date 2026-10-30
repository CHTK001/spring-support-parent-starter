package com.chua.starter.common.support.jackson;

import com.chua.common.support.utils.DateUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;

import java.io.IOException;
import java.time.LocalTime;

/**
 * 通用本地时间解析
 * @since 2024/12/19
 * @author CH
 */
public class CommonLocalTimeDeserializer extends LocalTimeDeserializer {

    /**
     * 日志
     */
    private static final System.Logger LOG = System.getLogger(CommonLocalTimeDeserializer.class.getName());

    @Override
    /**
     * 反序列化
     * @param context 参数
     * @param parser 参数
     * @return 返回值
     * @throws IOException 异常
    */
    public LocalTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        IOException ex;
        try {
            return super.deserialize(parser, context);
        } catch (IOException e) {
            ex = e;
        }

        if(parser.hasTokenId(JsonTokenId.ID_STRING)) {
            try {
                return DateUtils.toLocalTime(parser.getText());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.DEBUG, "[jackson] 解析 LocalTime 失败，回退原始异常", e);
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

