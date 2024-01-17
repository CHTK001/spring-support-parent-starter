package com.chua.starter.common.support.jackson;

import com.chua.common.support.json.Json;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
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
        return treeNodeToValue(treeNode);
    }

    /**
     * 树节点到值
     *
     * @param treeNode 树节点
     * @return {@link Object}
     */
    private Object treeNodeToValue(TreeNode treeNode) {
        if (treeNode.isArray()) {
            return treeNodeToArray(treeNode);
        }

        if (treeNode.isObject()) {
            return treeNodeToObject(treeNode);
        }

        if (treeNode.isValueNode()) {
            return treeNode.toString();
        }
        return null;
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
            return treeNode.toString();
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
    private Object treeNodeToObject(TreeNode treeNode) {
        Iterator<String> stringIterator = treeNode.fieldNames();
        Map<String, Object> rs = new LinkedHashMap<>();
        stringIterator.forEachRemaining(it -> {
            rs.put(it, treeNodeToValueValue(treeNode.get(it)));
        });
        return Json.toJson(rs);
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
    private Object treeNodeToArray(TreeNode treeNode) {
        int size = treeNode.size();
        List<Object> rs = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            TreeNode treeNode1 = treeNode.get(i);
            rs.add(treeNodeToValueValue(treeNode1));
        }
        return Json.toJson(rs);
    }
}
