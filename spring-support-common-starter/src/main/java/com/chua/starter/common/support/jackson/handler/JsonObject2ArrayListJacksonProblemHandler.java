package com.chua.starter.common.support.jackson.handler;

import com.chua.common.support.function.Joiner;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * 数组转字符串
 *
 * @author CH
 * @since 2024/7/19
 */
public class JsonObject2ArrayListJacksonProblemHandler implements JacksonProblemHandler {

    @Override
    public Object handle(DeserializationContext ctxt, JavaType targetType, JsonToken t, JsonParser p, String failureMsg) {
        boolean expectedStartObjectToken = p.isExpectedStartObjectToken();
        try {
            if (expectedStartObjectToken) {
                List<String> sb = new LinkedList<>();
                while (true) {
                    JsonToken token = p.nextToken();
                    if (token == JsonToken.END_ARRAY) {
                        break;
                    }
                    sb.add(p.getText());
                }
                return Joiner.on(",").join(sb);
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}

