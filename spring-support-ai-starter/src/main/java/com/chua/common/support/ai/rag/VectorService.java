package com.chua.common.support.ai.rag;

import java.util.List;

public interface VectorService {

    String name();

    default void init(com.chua.common.support.ai.rag.RagClientSetting setting) {
    }

    default void close() {
    }
}
