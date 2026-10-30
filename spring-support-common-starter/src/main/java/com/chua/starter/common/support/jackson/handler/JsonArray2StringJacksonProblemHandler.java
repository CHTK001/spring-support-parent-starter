package com.chua.starter.common.support.jackson.handler;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * 数组转字符串
 * @since 2024/7/19
 * @author CH
 */
public class JsonArray2StringJacksonProblemHandler implements JacksonProblemHandler{

    /**
     * 日志
     */
    private static final System.Logger LOG = System.getLogger(JsonArray2StringJacksonProblemHandler.class.getName());

    @Override
    /**
     * 处理
     * @param ctxt 参数
     * @param failureMsg 字符串
     * @param p 参数
     * @param t 参数
     * @param targetType 参数
     * @return 返回值
    */
    public Object handle(DeserializationContext ctxt, JavaType targetType, JsonToken t, JsonParser p, String failureMsg) {
        boolean expectedStartArrayToken = p.isExpectedStartArrayToken();
        try {
            if (expectedStartArrayToken) {
                List<String> sb = new LinkedList<>();
                while (true) {
                    JsonToken token = p.nextToken();
                    if (token == JsonToken.END_ARRAY) {
                        break;
                    }
                    sb.add(p.getText());
                }
                return java.lang.String.join(",", sb);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "[jackson] 数组转字符串失败: " + e.getMessage(), e);
        }
        return null;
    }
}
