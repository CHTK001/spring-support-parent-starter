package com.chua.common.support.ai;

import java.util.List;

public interface VectorStorage {

    void upsert(String documentId, String content, float[] vector, java.util.Map<String, Object> metadata);

    List<float[]> search(float[] queryVector, int topK);

    void delete(String documentId);

    void clear();
}
