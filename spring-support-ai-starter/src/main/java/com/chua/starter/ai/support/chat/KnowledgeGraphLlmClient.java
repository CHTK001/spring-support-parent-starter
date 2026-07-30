package com.chua.starter.ai.support.chat;

import java.io.Closeable;

public interface KnowledgeGraphLlmClient extends Closeable {

    String chat(String input);

    void close();
}
