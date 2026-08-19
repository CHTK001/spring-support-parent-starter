package com.chua.starter.ai.support.configuration.rag;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * RAG 模块全局设置提供者 SPI。
 *
 * @author CH
 */
@Spi("rag")
public class RagGlobalSettingProvider implements GlobalSettingProvider {

    @Override
    /** 分组 */
    public String group() {
        return "rag";
    }

    @Override
    /** 分组Name */
    public String groupName() {
        return "RAG 知识库";
    }

    @Override
    /** 获取Items */
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("rag_embedding_engine", "memory", "STRING", "Embedding 引擎（memory / openai / siliconflow）", 1),
                new SettingItem("rag_vector_storage", "jvector", "STRING", "向量存储（jvector / milvus / mysql / memory）", 2),
                new SettingItem("rag_chunk_size", "1000", "NUMBER", "分块大小", 3),
                new SettingItem("rag_chunk_overlap", "200", "NUMBER", "分块重叠", 4),
                new SettingItem("rag_default_top_k", "5", "NUMBER", "默认检索 Top-K", 5),
                new SettingItem("rag_similarity_threshold", "0.7", "NUMBER", "相似度阈值", 6)
        );
    }
}
