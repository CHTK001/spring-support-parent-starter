package com.chua.starter.common.support.jackson;

import com.chua.common.support.converter.Converter;
import com.chua.common.support.lang.json.Json;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
* 自定义json日期反序列化程序
*
* @author CH
* @since 4.0.0
 */
public class ArrayToJsonStringJsonDeserializer extends JsonDeserializer<Object> {


    @Override
    /** 反序列化 */
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
    * @return {@link 对象}
     */
    private Object treeNodeToValue(TreeNode treeNode) {
        return treeNodeToValueValue(treeNode);
    }
    /**
    * 树节点到值
    *
    * @param treeNode 树节点
    * @return {@link 对象}
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

    /**
    * 树节点转为新价值观
    *
    * @param treeNode 树节点
    * @return 树节点转为新价值观的结果
     */
    private Object treeNodeToNewValue(TreeNode treeNode) {
        JsonToken token = treeNode.asToken();
        if(token.isBoolean()) {
            return Boolean.valueOf(treeNode.toString());
        }


        if(token.isNumeric()) {
            JsonParser.NumberType numberType = treeNode.numberType();
            if(numberType == JsonParser.NumberType.INT) {
                return Converter.convertIfNecessary(treeNode.toString(), Integer.class);
            }

            if(numberType == JsonParser.NumberType.LONG) {
                return Converter.convertIfNecessary(treeNode.toString(), Long.class);
            }

            if(numberType == JsonParser.NumberType.FLOAT) {
                return Converter.convertIfNecessary(treeNode.toString(), Float.class);
            }

            if(numberType == JsonParser.NumberType.DOUBLE) {
                return Converter.convertIfNecessary(treeNode.toString(), Double.class);
            }

            if(numberType == JsonParser.NumberType.BIG_DECIMAL) {
                /**
                * bigdecimal。
                * @param treeNode.toString( 树节点.tostring(
                * @return BigDecimal的结果
                 */
                return new BigDecimal(treeNode.toString());
            }

            if(numberType == JsonParser.NumberType.BIG_INTEGER) {
                /**
                * biginteger。
                * @param treeNode.toString( 树节点.tostring(
                * @return BigInteger的结果
                 */
                return new BigInteger(treeNode.toString());
            }
        }

        return null;

    }

    /**
    * 树节点转为对象价值观
    *
    * @param treeNode 树节点
    * @return 树节点转为对象价值观的结果
     */
    private Object treeNodeToObjectValue(TreeNode treeNode) {
        Iterator<String> stringIterator = treeNode.fieldNames();
        Map<String, Object> rs = new LinkedHashMap<>();
        stringIterator.forEachRemaining(it -> {
            rs.put(it, treeNodeToValue(treeNode.get(it)));
        });
        return rs;
    }

    /**
    * 树节点转为array价值观
    *
    * @param treeNode 树节点
    * @return 树节点转为array价值观的结果
     */
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

