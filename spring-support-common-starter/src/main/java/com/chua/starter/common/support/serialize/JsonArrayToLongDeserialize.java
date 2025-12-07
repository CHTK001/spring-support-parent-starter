package com.chua.starter.common.support.serialize;

import com.chua.common.support.converter.Converter;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * @author CH
 */
public class JsonArrayToLongDeserialize  extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        TreeNode treeNode = p.getCodec().readTree(p);
        if (treeNode.isArray()) {
            Long[] rs = new Long[treeNode.size()];
            for (int i = 0; i < rs.length; i++) {
                TreeNode treeNode1 = treeNode.get(i);
                rs[i] = Converter.convertIfNecessary(JsonArrayToStringDeserialize.analysisValue(treeNode1), Long.class);
            }
            return rs[rs.length - 1];
        }

        return Converter.convertIfNecessary(JsonArrayToStringDeserialize.analysisValue(treeNode), Long.class);
    }

}

