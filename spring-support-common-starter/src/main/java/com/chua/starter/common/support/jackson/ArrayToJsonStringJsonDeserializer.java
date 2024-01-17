package com.chua.starter.common.support.jackson;

import com.chua.common.support.json.Json;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * 自定义json日期反序列化程序
 *
 * @author CH
 */
public class ArrayToJsonStringJsonDeserializer extends JsonDeserializer<Object> {


    @Override
    public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        TreeNode treeNode = jsonParser.getCodec().readTree(jsonParser);
        Object o = treeNodeToValue(treeNode);
        if(null == o) {
            return null;
        }

        return Json.toJson(o);
    }

    /**
     * 树节点到值
     *
     * @param treeNode 树节点
     * @return {@link Object}
     */
    private Object treeNodeToValue(TreeNode treeNode) {
        return treeNodeToValueValue(treeNode);
    }
    /**
     * 树节点到值
     *
     * @param treeNode 树节点
     * @return {@link Object}
     */
    private Object treeNodeToValueValue(TreeNode treeNode) {
        if (treeNode.isArray()) {
            return treeNodeToArrayValue(treeNode);
        }

        if (treeNode.isObject()) {
            return treeNodeToObjectValue(treeNode);
        }

        if (treeNode.isValueNode()) {
            return treeNodeToNewValue(treeNode);
        }

        if(treeNode.isMissingNode()) {
            return null;
        }
        return null;
    }

    private Object treeNodeToNewValue(TreeNode treeNode) {
        JsonToken token = treeNode.asToken();
        if(token.isBoolean()) {
            return Boolean.valueOf(treeNode.toString());
        }


        if(token.isNumeric()) {
            JsonParser.NumberType numberType = treeNode.numberType();
            if(numberType == JsonParser.NumberType.INT) {
                return Integer.valueOf(treeNode.toString());
            }

            if(numberType == JsonParser.NumberType.LONG) {
                return Long.valueOf(treeNode.toString());
            }

            if(numberType == JsonParser.NumberType.FLOAT) {
                return Float.valueOf(treeNode.toString());
            }

            if(numberType == JsonParser.NumberType.DOUBLE) {
                return Double.valueOf(treeNode.toString());
            }

            if(numberType == JsonParser.NumberType.BIG_DECIMAL) {
                return new BigDecimal(treeNode.toString());
            }

            if(numberType == JsonParser.NumberType.BIG_INTEGER) {
                return new BigInteger(treeNode.toString());
            }
        }

        return null;

    }

    private Object treeNodeToObjectValue(TreeNode treeNode) {
        Iterator<String> stringIterator = treeNode.fieldNames();
        Map<String, Object> rs = new LinkedHashMap<>();
        stringIterator.forEachRemaining(it -> {
            rs.put(it, treeNodeToValue(treeNode.get(it)));
        });
        return rs;
    }

    private Object treeNodeToArrayValue(TreeNode treeNode) {
        int size = treeNode.size();
        List<Object> rs = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            TreeNode treeNode1 = treeNode.get(i);
            rs.add(treeNodeToValue(treeNode1));
        }
        return rs;
    }
}
