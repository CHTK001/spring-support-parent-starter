package com.chua.starter.ai.support.service.impl.settings;

import com.chua.starter.ai.support.configuration.GlobalChatClientManager;
import com.chua.starter.ai.support.configuration.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.RagClientManager;
import com.chua.starter.ai.support.model.AiGlobalSettingDTO;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.settings.AiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局设置服务实现类。
 *
 * <p>全局设置存储于 {@code sys_ai_global_setting} 键值表（分组 {@code global}），
 * 各字段以键值行保存，与旧版 runtime 配置共用同一张表。</p>
 *
 * @author CH
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGlobalSettingServiceImpl implements AiGlobalSettingService {

    /**
     * 全局设置键值分组
     */
    private static final String GROUP = "global";

    /**
     * 键值全局设置服务
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

    /**
     * 全局 RAG 客户端管理器（保存后热重建）
     */
    private final RagClientManager ragClientManager;

    /**
     * 全局 ChatClient 管理器（保存后热重建全局 ChatClient 与主 Agent）
     */
    private final GlobalChatClientManager globalChatClientManager;

    /**
     * 获取全局设置。
     *
     * @return 全局设置 DTO
     */
    @Override
    public AiGlobalSettingDTO get() {
        AiGlobalSettingDTO dto = new AiGlobalSettingDTO();
        dto.setChatClientEnabled(settingReader.getBool(GROUP, "chat_client_enabled", true));
        dto.setEnableUsageAnalysis(settingReader.getBool(GROUP, "enable_usage_analysis", true));
        dto.setUsageReportInterval(settingReader.getIntOrNull(GROUP, "usage_report_interval"));
        dto.setDefaultModel(settingReader.getStr(GROUP, "default_model"));
        dto.setTemperature(settingReader.getDecimalOrNull(GROUP, "temperature"));
        dto.setMaxTokens(settingReader.getIntOrNull(GROUP, "max_tokens"));
        dto.setTimeout(settingReader.getIntOrNull(GROUP, "timeout"));
        dto.setEnableCache(settingReader.getBool(GROUP, "enable_cache", true));
        dto.setStream(settingReader.getBool(GROUP, "stream", true));
        dto.setSystemPrompt(settingReader.getStr(GROUP, "system_prompt"));
        dto.setRagEmbeddingModel(settingReader.getStr(GROUP, "rag_embedding_model"));
        dto.setRagLlmModel(settingReader.getStr(GROUP, "rag_llm_model"));
        dto.setRagOcrModel(settingReader.getStr(GROUP, "rag_ocr_model"));
        dto.setRagUploadDir(settingReader.getStr(GROUP, "rag_upload_dir"));
        dto.setRagVectorStorage(settingReader.getStr(GROUP, "rag_vector_storage"));
        dto.setRagSystemPrompt(settingReader.getStr(GROUP, "rag_system_prompt"));
        dto.setRagChunkSize(settingReader.getIntOrNull(GROUP, "rag_chunk_size"));
        dto.setRagChunkOverlap(settingReader.getIntOrNull(GROUP, "rag_chunk_overlap"));
        dto.setRagSplitter(settingReader.getStr(GROUP, "rag_splitter"));
        return dto;
    }

    /**
     * 更新全局设置，非空字段覆盖写入键值表。
     *
     * @param setting 全局设置 DTO
     * @return 更新后的全局设置 DTO
     */
    @Override
    @Transactional
    public AiGlobalSettingDTO update(AiGlobalSettingDTO setting) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("group", GROUP);
        List<Map<String, Object>> fields = new ArrayList<>();
        putIfNotNull(fields, "chat_client_enabled", setting.getChatClientEnabled());
        putIfNotNull(fields, "enable_usage_analysis", setting.getEnableUsageAnalysis());
        putIfNotNull(fields, "usage_report_interval", setting.getUsageReportInterval());
        putIfNotNull(fields, "default_model", setting.getDefaultModel());
        putIfNotNull(fields, "temperature", setting.getTemperature());
        putIfNotNull(fields, "max_tokens", setting.getMaxTokens());
        putIfNotNull(fields, "timeout", setting.getTimeout());
        putIfNotNull(fields, "enable_cache", setting.getEnableCache());
        putIfNotNull(fields, "stream", setting.getStream());
        putIfNotNull(fields, "system_prompt", setting.getSystemPrompt());
        putIfNotNull(fields, "rag_embedding_model", setting.getRagEmbeddingModel());
        putIfNotNull(fields, "rag_llm_model", setting.getRagLlmModel());
        putIfNotNull(fields, "rag_ocr_model", setting.getRagOcrModel());
        putIfNotNull(fields, "rag_upload_dir", setting.getRagUploadDir());
        putIfNotNull(fields, "rag_vector_storage", setting.getRagVectorStorage());
        putIfNotNull(fields, "rag_system_prompt", setting.getRagSystemPrompt());
        putIfNotNull(fields, "rag_chunk_size", setting.getRagChunkSize());
        putIfNotNull(fields, "rag_chunk_overlap", setting.getRagChunkOverlap());
        putIfNotNull(fields, "rag_splitter", setting.getRagSplitter());
        request.put("fields", fields);
        sysAiGlobalSettingService.replace(request);
        log.debug("[AiGlobalSetting] 全局配置已保存到键值表 {}", GROUP);

        // 全局配置热更新：重建全局 ChatClient / 主 Agent 与 RAG 客户端
        try {
            globalChatClientManager.rebuild();
        } catch (Exception e) {
            log.warn("[AiGlobalSetting] 全局 ChatClient 热更新失败: {}", e.getMessage());
        }
        try {
            ragClientManager.rebuild();
        } catch (Exception e) {
            log.warn("[AiGlobalSetting] RAG 客户端热更新失败: {}", e.getMessage());
        }
        return get();
    }

    /**
     * 非空值写入字段列表。
     */
    private void putIfNotNull(List<Map<String, Object>> fields, String field, Object value) {
        if (value == null) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("field", field);
        item.put("value", String.valueOf(value));
        item.put("valueType", "STRING");
        item.put("description", field);
        fields.add(item);
    }
}
