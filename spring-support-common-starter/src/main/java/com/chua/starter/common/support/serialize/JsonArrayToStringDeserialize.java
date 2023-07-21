package com.chua.starter.common.support.serialize;

import com.chua.common.support.converter.Converter;
import com.chua.common.support.json.Json;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author CH
 */
public class JsonArrayToStringDeserialize extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        TreeNode treeNode = p.getCodec().readTree(p);
        if (treeNode.isArray()) {
            Object[] rs = new Object[treeNode.size()];
            for (int i = 0; i < rs.length; i++) {
                TreeNode treeNode1 = treeNode.get(i);
                rs[i] = analysisValue(treeNode1);
            }
            return Json.toJson(rs);
        }

        return Converter.convertIfNecessary(analysisValue(treeNode), String.class);
    }

    public static Object analysisValue(TreeNode treeNode1) {
        if (treeNode1 instanceof IntNode) {
            return ((IntNode) treeNode1).asInt();
        }

        if (treeNode1 instanceof LongNode) {
            return ((LongNode) treeNode1).asLong();
        }

        if (treeNode1 instanceof FloatNode) {
            return ((FloatNode) treeNode1).asDouble();
        }

        if (treeNode1 instanceof DoubleNode) {
            return ((DoubleNode) treeNode1).asDouble();
        }

        if (treeNode1 instanceof DecimalNode) {
            return new BigDecimal(((DecimalNode) treeNode1).asText());
        }

        if (treeNode1 instanceof BigIntegerNode) {
            return BigInteger.valueOf(((BigIntegerNode) treeNode1).asLong());
        }


        if (treeNode1 instanceof BooleanNode) {
            return ((BooleanNode) treeNode1).asBoolean();
        }

        if (treeNode1 instanceof TextNode) {
            return ((TextNode) treeNode1).asText();
        }

        if (treeNode1 instanceof ShortNode) {
            return ((ShortNode) treeNode1).asInt();
        }

        if (treeNode1.isArray()) {
            Object[] rs = new Object[treeNode1.size()];
            for (int i = 0; i < rs.length; i++) {
                TreeNode treeNode2 = treeNode1.get(i);
                rs[i] = analysisValue(treeNode2);
            }
            return rs;
        }

        return null;
    }
}
