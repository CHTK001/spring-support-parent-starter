package com.chua.starter.ai.support.chat;

import java.io.Closeable;

/**
 * 知识图谱 LLM 客户端接口。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface KnowledgeGraphLlmClient extends Closeable {

    String chat(String input);

    void close();
}
