package com.chua.starter.spider.support.serializer;

/**
 * 编排定义反序列化异常，消息中包含具体字段路径。
 *
 * @author CH
 */
public class SpiderFlowDeserializeException extends RuntimeException {

    private final String fieldPath;

    public SpiderFlowDeserializeException(String fieldPath, String message) {
        super("[" + fieldPath + "] " + message);
        this.fieldPath = fieldPath;
    }

    public String getFieldPath() {
        return fieldPath;
    }
}
