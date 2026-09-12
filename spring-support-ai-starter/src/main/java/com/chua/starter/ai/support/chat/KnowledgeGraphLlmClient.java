package com.chua.starter.ai.support.chat;

import java.io.Closeable;

/**
* 知识图谱 LLM 客户端接口。
*
* @since 4.0.0.42
* @author CH
 */
public interface KnowledgeGraphLlmClient extends Closeable {

    /**
    * 对话。
    * @param input 输入
    * @return 对话的结果
     */
    String chat(String input);

    /**
    * 关闭。
     */
    void close();
}
