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
    /**
     * 分组
    */
    public String group() {
        return "rag";
    }

    @Override
    /**
     * 分组名称
    */
    public String groupName() {
        return "RAG 知识库";
    }

    @Override
    /**
     * 获取Items
    */
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("rag_embedding_model", "memory", "STRING", "Embedding 引擎（memory / openai / siliconflow / sensetime / onnx）", 1),
                new SettingItem("rag_vector_storage", "jvector", "STRING", "向量存储（jvector / milvus / mysql / memory）", 2),
                new SettingItem("rag_llm_model", "", "STRING", "RAG 问答使用的 LLM 模型名（留空走全局默认）", 3),
                new SettingItem("rag_chunk_size", "1000", "NUMBER", "分块大小", 4),
                new SettingItem("rag_chunk_overlap", "200", "NUMBER", "分块重叠", 5),
                new SettingItem("rag_upload_dir", "./rag-uploads", "STRING", "文档上传目录", 6),
                new SettingItem("rag_ocr_model", "", "STRING", "OCR 识别模型（可选）", 7),
                new SettingItem("rag_system_prompt", "", "STRING", "RAG 问答系统提示词（可选）", 8),
                new SettingItem("rag_splitter", "sentence", "STRING", "文本分割器（sentence / regex / paragraph）", 9),
                new SettingItem("rag_default_top_k", "5", "NUMBER", "默认检索 Top-K", 10),
                new SettingItem("rag_similarity_threshold", "0.7", "NUMBER", "相似度阈值", 11),
                new SettingItem("rag_embedding_dimensions", "1536", "NUMBER", "向量维度（与 Embedding 模型输出维度一致）", 12),
                new SettingItem("rag_embedding_base_url", "", "STRING", "Embedding API 基础地址（留空走 runtime 配置）", 14),
                new SettingItem("rag_embedding_api_key", "", "STRING", "Embedding API 密钥（留空走 runtime 配置）", 15),
                new SettingItem("rag_embedding_model_name", "", "STRING", "Embedding 模型标识（如 nvidia/nemotron-3-embed-1b:free）", 16),
                new SettingItem("rag_client_provider", "pipeline", "STRING", "RAG 客户端类型（pipeline 管线）", 13),
                new SettingItem("rag_relation_extract_enabled", "true", "BOOLEAN",
                        "上传知识点时是否抽取实体关系（人物/地点/事件）并写入知识图谱", 17),
                new SettingItem("rag_relation_extract_model", "", "STRING",
                        "关系抽取使用的 LLM 模型（留空复用 RAG 问答的 LLM）", 18)
        );
    }
}
