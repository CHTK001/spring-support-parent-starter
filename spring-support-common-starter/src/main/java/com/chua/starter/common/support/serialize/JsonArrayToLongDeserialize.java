package com.chua.starter.common.support.serialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.chua.common.support.converter.Converter;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * JSON 数组转 Long 反序列化器
 *
 * @author CH
 */
public class JsonArrayToLongDeserialize  extends JsonDeserializer<Long> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JsonArrayToLongDeserialize.class);


    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        TreeNode treeNode = p.getCodec().readTree(p);
        if (log.isTraceEnabled()) {
            log.trace("[反序列化]使用 JsonArrayToLongDeserialize 反序列化, 节点类型: {}", treeNode.getClass().getSimpleName());
        }
        if (treeNode.isArray()) {
            Long[] rs = new Long[treeNode.size()];
            for (int i = 0; i < rs.length; i++) {
                TreeNode treeNode1 = treeNode.get(i);
                rs[i] = Converter.convertIfNecessary(JsonArrayToStringDeserialize.analysisValue(treeNode1), Long.class);
            }
            Long result = rs[rs.length - 1];
            if (log.isTraceEnabled()) {
                log.trace("[反序列化]数组转 Long 完成, 数组长度: {}, 结果: {}", rs.length, result);
            }
            return result;
        }

        Long result = Converter.convertIfNecessary(JsonArrayToStringDeserialize.analysisValue(treeNode), Long.class);
        if (log.isTraceEnabled()) {
            log.trace("[反序列化]非数组节点转 Long, 结果: {}", result);
        }
        return result;
    }

}

