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

        // 探测 RAG 客户端是否可用。
        // RAG 未配置时 current() 会走到未初始化的嵌入实现并抛 NoClassDefFoundError（Error 而非 Exception，
        // 常规 catch (Exception) 兜不住），这里显式声明，这里要的是「状态」而不是「异常」。
        boolean ragAvailable = false;
        try {
            ragAvailable = ragClientManager.current() != null;
        } catch (Throwable e) {
            log.debug("[RagStatus] RAG 客户端不可用（未配置 RAG 参数）: {}", e.toString());
        }
        status.put("available", ragAvailable);

        // 获取当前配置描述
        Map<String, String> config = new HashMap<>();
        try {
            Map<String, String> described = ragClientManager.describe();
            if (described != null) {
                config.putAll(described);
            }
        } catch (Throwable e) {
            log.debug("[RagStatus] 读取 RAG 配置失败: {}", e.toString());
        }
        status.put("config", config);

        // 检查是否需要配置
        boolean needLlmConfig = config.get("llm_model") == null || config.get("llm_model").isEmpty();
        // embedding 自检：不再只看引擎是否 memory —— 引擎对但模型 id / 凭证缺失时同样不可用，
        // 而 createEmbeddingClient 会静默回落成内存占位，状态必须如实报出来。
        String embeddingIssue = null;
        try {
            embeddingIssue = ragClientManager.embeddingIssue();
        } catch (Throwable e) {
            embeddingIssue = "embedding 自检异常: " + e;
            log.debug("[RagStatus] embedding 自检失败: {}", e.toString());
        }
        boolean needEmbeddingConfig = embeddingIssue != null;
        status.put("embeddingReady", !needEmbeddingConfig);
        if (needEmbeddingConfig) {
            status.put("embeddingIssue", embeddingIssue);
        }
        boolean needVectorStorageConfig = config.get("vector_storage") == null || config.get("vector_storage").isEmpty();

        status.put("needLlmConfig", needLlmConfig);
        status.put("needEmbeddingConfig", needEmbeddingConfig);
        status.put("needVectorStorageConfig", needVectorStorageConfig);

        // 提示信息
        if (!ragAvailable) {
            status.put("message", "RAG 服务未初始化，请先配置对话模型");
            status.put("configPath", "/settings/default-models");
        } else if (needEmbeddingConfig) {
            status.put("message", embeddingIssue);
            status.put("configPath", "/settings/rag");
        } else {
            status.put("message", "RAG 服务正常");
            status.put("configPath", null);
        }

        return ReturnResult.success(status);
    }
}
