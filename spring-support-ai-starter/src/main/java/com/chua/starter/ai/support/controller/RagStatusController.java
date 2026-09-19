package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.configuration.rag.RagClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * RAG 配置状态控制器。
 *
 * <p>提供 RAG 配置状态查询接口，前端用于判断是否需要提示用户配置。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/rag")
@RequiredArgsConstructor
public class RagStatusController {

    private final RagClientManager ragClientManager;

    /**
     * 获取 RAG 配置状态
     */
    @GetMapping("/status")
    public ReturnResult<Map<String, Object>> getRagStatus() {
        Map<String, Object> status = new HashMap<>();

        // 检查 RAG 客户端是否可用
        boolean ragAvailable = ragClientManager.current() != null;
        status.put("available", ragAvailable);

        // 获取当前配置描述
        Map<String, String> config = ragClientManager.describe();
        status.put("config", config);

        // 检查是否需要配置
        boolean needLlmConfig = config.get("llm_model") == null || config.get("llm_model").isEmpty();
        boolean needEmbeddingConfig = "memory".equalsIgnoreCase(config.get("embedding_model"));
        boolean needVectorStorageConfig = config.get("vector_storage") == null || config.get("vector_storage").isEmpty();

        status.put("needLlmConfig", needLlmConfig);
        status.put("needEmbeddingConfig", needEmbeddingConfig);
        status.put("needVectorStorageConfig", needVectorStorageConfig);

        // 提示信息
        if (!ragAvailable) {
            status.put("message", "RAG 服务未初始化，请先配置对话模型");
            status.put("configPath", "/settings/default-models");
        } else if (needEmbeddingConfig) {
            status.put("message", "未配置向量模型，知识库功能将使用内存向量存储（不持久化）");
            status.put("configPath", "/settings/rag");
        } else {
            status.put("message", "RAG 服务正常");
            status.put("configPath", null);
        }

        return ReturnResult.success(status);
    }
}
