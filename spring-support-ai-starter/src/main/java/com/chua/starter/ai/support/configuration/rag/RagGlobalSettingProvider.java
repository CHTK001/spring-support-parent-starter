package com.chua.starter.ai.support.configuration.rag;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
* RAG 模块全局设置提供者 SPI。
*
* @author CH
* @since 4.0.0
 */
@Spi("rag")
public class RagGlobalSettingProvider implements GlobalSettingProvider {

    @Override
    /** 分组 */
    public String group() {
        return "rag";
    }

    @Override
    /** 分组名称 */
    public String groupName() {
        return "RAG 知识库";
    }

    @Override
    /** 获取Items */
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("rag_embedding_model", "memory", "STRING", "Embedding 模型（memory / openai / siliconflow）", 1),
                new SettingItem("rag_vector_storage", "jvector", "STRING", "向量存储（jvector / milvus / mysql / memory）", 2),
                new SettingItem("rag_llm_model", "", "STRING", "RAG 问答使用的 LLM 模型名（留空走全局默认）", 3),
                new SettingItem("rag_chunk_size", "1000", "NUMBER", "分块大小", 4),
                new SettingItem("rag_chunk_overlap", "200", "NUMBER", "分块重叠", 5),
                new SettingItem("rag_upload_dir", "./rag-uploads", "STRING", "文档上传目录", 6),
                new SettingItem("rag_ocr_model", "", "STRING", "OCR 识别模型（可选）", 7),
                new SettingItem("rag_system_prompt", "", "STRING", "RAG 问答系统提示词（可选）", 8),
                new SettingItem("rag_splitter", "sentence", "STRING", "文本分割器（sentence / regex / paragraph）", 9),
                new SettingItem("rag_default_top_k", "5", "NUMBER", "默认检索 Top-K", 10),
                new SettingItem("rag_similarity_threshold", "0.7", "NUMBER", "相似度阈值", 11)
        );
    }
}
