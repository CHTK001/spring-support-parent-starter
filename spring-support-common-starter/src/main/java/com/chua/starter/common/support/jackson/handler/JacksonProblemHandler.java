package com.chua.starter.common.support.jackson.handler;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;

/**
 * JacksonProblemHandler 接口定义了一个处理Jackson序列化和反序列化问题的策略�?
 * 实现这个接口的类需要指定源类型和目标类型，并提供一个处理方法来转换或处理对象�?
 * @author CH
 * @since 2024/7/19
 */
public interface JacksonProblemHandler {


    /**
     * 处理反序列化过程中的特定逻辑�?
     *
     * 此方法旨在处理反序列化时的特殊情况，例如当遇到预期之外的JSON令牌或需要特殊处理目标类型时�?
     * 它通过分析上下文、目标类型、当前JSON令牌和解析器状态来决定如何处理当前的反序列化操作�?
     *
     * @param ctxt 反序列化上下文，提供关于当前反序列化操作的环境信息�?
     * @param targetType 需要被反序列化的对象的目标类型�?
     * @param t 当前遇到的JSON令牌，用于理解JSON文档的结构和内容�?
     * @param p JSON解析器，用于读取和解析JSON数据�?
     * @param failureMsg 如果处理失败，提供一个描述性错误消息�?
     * @return 反序列化后的对象，或者在处理失败时可能返回null或抛出异常�?
     */
    Object handle(DeserializationContext ctxt, JavaType targetType, JsonToken t, JsonParser p, String failureMsg);

}


