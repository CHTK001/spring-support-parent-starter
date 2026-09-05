package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiGlobalSettingDTO;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 全局设置服务实现（单条记录 id=1 的语义，落地到 {@code sys_ai_global_setting} 键值表）。
 *
 * <p>把前端提交的扁平 DTO 字段（{@code chatClientEnabled} / {@code defaultModel} / RAG 系列等）
 * 映射为不同分组的键值行并持久化：LLM 运行时相关字段落到 {@code runtime} 分组，
 * RAG 字段落到 {@code rag} 分组，OCR 模型字段落到 {@code ocr} 分组；
 * DTO 中无 SPI 默认项对应的字段（用量分析/缓存/流式等）落到 {@code global} 分组兜底，
 * 保证读-写往返一致，并可供 {@link com.chua.starter.ai.support.configuration.client.GlobalSettingReader}
 * 类型安全消费。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGlobalSettingServiceImpl implements AiGlobalSettingService {

    /**
     * 键值全局设置服务（读写 sys_ai_global_setting 表）
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * DTO 字段与 (分组, 表字段) 的映射。
     * 无 SPI 对应字段的配置项统一落到 {@code global} 分组，保持往返一致。
     */
    private static final java.util.Map<String, String[]> FIELD_MAP = fieldMap();

    private static java.util.Map<String, String[]> fieldMap() {
        java.util.Map<String, String[]> m = new java.util.LinkedHashMap<>();
        // 字段名 -> [分组, 表字段名]
        m.put("chatClientEnabled", new String[]{"runtime", "enabled"});
        m.put("enableUsageAnalysis", new String[]{"global", "enable_usage_analysis"});
        m.put("usageReportInterval", new String[]{"global", "usage_report_interval"});
        m.put("defaultModel", new String[]{"runtime", "default_model"});
        m.put("temperature", new String[]{"runtime", "temperature"});
        m.put("maxTokens", new String[]{"runtime", "max_tokens"});
        m.put("timeout", new String[]{"runtime", "timeout_millis"});
        m.put("enableCache", new String[]{"global", "enable_cache"});
        m.put("stream", new String[]{"global", "stream"});
        m.put("systemPrompt", new String[]{"runtime", "system_prompt"});
        m.put("ragEmbeddingModel", new String[]{"rag", "rag_embedding_engine"});
        m.put("ragLlmModel", new String[]{"global", "rag_llm_model"});
        m.put("ragOcrModel", new String[]{"ocr", "default_ocr_model"});
        m.put("ragUploadDir", new String[]{"global", "rag_upload_dir"});
        m.put("ragVectorStorage", new String[]{"rag", "rag_vector_storage"});
        m.put("ragSystemPrompt", new String[]{"global", "rag_system_prompt"});
        m.put("ragChunkSize", new String[]{"rag", "rag_chunk_size"});
        m.put("ragChunkOverlap", new String[]{"rag", "rag_chunk_overlap"});
        m.put("ragSplitter", new String[]{"global", "rag_splitter"});
        m.put("workshopStorageDir", new String[]{"workshop", "storage_dir"});
        m.put("workshopMaxUploadSizeMb", new String[]{"workshop", "max_upload_size_mb"});
        m.put("workshopAllowedExtensions", new String[]{"workshop", "allowed_extensions"});
        m.put("protocolServerEnabled", new String[]{"runtime", "protocol_server_enabled"});
        m.put("protocolServerPort", new String[]{"runtime", "protocol_server_port"});
        return java.util.Map.copyOf(m);
    }

    @Override
    public AiGlobalSettingDTO get() {
        AiGlobalSettingDTO dto = new AiGlobalSettingDTO();
        dto.setChatClientEnabled(getBool("chatClientEnabled", null));
        dto.setEnableUsageAnalysis(getBool("enableUsageAnalysis", null));
        dto.setUsageReportInterval(getInt("usageReportInterval", null));
        dto.setDefaultModel(getStr("defaultModel"));
        dto.setTemperature(getDecimal("temperature", null));
        dto.setMaxTokens(getInt("maxTokens", null));
        dto.setTimeout(getInt("timeout", null));
        dto.setEnableCache(getBool("enableCache", null));
        dto.setStream(getBool("stream", null));
        dto.setSystemPrompt(getStr("systemPrompt"));
        dto.setRagEmbeddingModel(getStr("ragEmbeddingModel"));
        dto.setRagLlmModel(getStr("ragLlmModel"));
        dto.setRagOcrModel(getStr("ragOcrModel"));
        dto.setRagUploadDir(getStr("ragUploadDir"));
        dto.setRagVectorStorage(getStr("ragVectorStorage"));
        dto.setRagSystemPrompt(getStr("ragSystemPrompt"));
        dto.setRagChunkSize(getInt("ragChunkSize", null));
        dto.setRagChunkOverlap(getInt("ragChunkOverlap", null));
        dto.setRagSplitter(getStr("ragSplitter"));
        dto.setWorkshopStorageDir(getStr("workshopStorageDir"));
        dto.setWorkshopMaxUploadSizeMb(getInt("workshopMaxUploadSizeMb", null));
        dto.setWorkshopAllowedExtensions(getStr("workshopAllowedExtensions"));
        dto.setProtocolServerEnabled(getBool("protocolServerEnabled", null));
        dto.setProtocolServerPort(getInt("protocolServerPort", null));
        return dto;
    }

    @Override
    public AiGlobalSettingDTO update(AiGlobalSettingDTO setting) {
        if (setting == null) {
            return get();
        }
        put("chatClientEnabled", setting.getChatClientEnabled());
        put("enableUsageAnalysis", setting.getEnableUsageAnalysis());
        put("usageReportInterval", setting.getUsageReportInterval());
        put("defaultModel", setting.getDefaultModel());
        put("temperature", setting.getTemperature());
        put("maxTokens", setting.getMaxTokens());
        put("timeout", setting.getTimeout());
        put("enableCache", setting.getEnableCache());
        put("stream", setting.getStream());
        put("systemPrompt", setting.getSystemPrompt());
        put("ragEmbeddingModel", setting.getRagEmbeddingModel());
        put("ragLlmModel", setting.getRagLlmModel());
        put("ragOcrModel", setting.getRagOcrModel());
        put("ragUploadDir", setting.getRagUploadDir());
        put("ragVectorStorage", setting.getRagVectorStorage());
        put("ragSystemPrompt", setting.getRagSystemPrompt());
        put("ragChunkSize", setting.getRagChunkSize());
        put("ragChunkOverlap", setting.getRagChunkOverlap());
        put("ragSplitter", setting.getRagSplitter());
        put("workshopStorageDir", setting.getWorkshopStorageDir());
        put("workshopMaxUploadSizeMb", setting.getWorkshopMaxUploadSizeMb());
        put("workshopAllowedExtensions", setting.getWorkshopAllowedExtensions());
        put("protocolServerEnabled", setting.getProtocolServerEnabled());
        put("protocolServerPort", setting.getProtocolServerPort());
        return get();
    }

    // ==================== 读取 ====================

    private String getStr(String dtoField) {
        String[] groupField = FIELD_MAP.get(dtoField);
        if (groupField == null) {
            return null;
        }
        return sysAiGlobalSettingService.getValue(groupField[0], groupField[1]).orElse(null);
    }

    private Boolean getBool(String dtoField, Boolean def) {
        String[] groupField = FIELD_MAP.get(dtoField);
        if (groupField == null) {
            return def;
        }
        java.util.Optional<String> opt = sysAiGlobalSettingService.getValue(groupField[0], groupField[1]);
        if (opt.isEmpty()) {
            return def;
        }
        String v = opt.get().trim().toLowerCase();
        return "true".equals(v) || "1".equals(v) || "yes".equals(v);
    }

    private Integer getInt(String dtoField, Integer def) {
        String[] groupField = FIELD_MAP.get(dtoField);
        if (groupField == null) {
            return def;
        }
        java.util.Optional<String> opt = sysAiGlobalSettingService.getValue(groupField[0], groupField[1]);
        if (opt.isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(opt.get().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private BigDecimal getDecimal(String dtoField, BigDecimal def) {
        String[] groupField = FIELD_MAP.get(dtoField);
        if (groupField == null) {
            return def;
        }
        java.util.Optional<String> opt = sysAiGlobalSettingService.getValue(groupField[0], groupField[1]);
        if (opt.isEmpty()) {
            return def;
        }
        try {
            return new BigDecimal(opt.get().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ==================== 写入 ====================

    private void put(String dtoField, Object value) {
        if (value == null) {
            return;
        }
        String[] groupField = FIELD_MAP.get(dtoField);
        if (groupField == null) {
            return;
        }
        String v = String.valueOf(value);
        if (v.isBlank()) {
            return;
        }
        String valueType = (value instanceof Boolean) ? "BOOLEAN"
                : (value instanceof Number) ? "NUMBER"
                : "STRING";
        try {
            sysAiGlobalSettingService.upsertField(groupField[0], groupField[1], v, valueType, null, 0);
        } catch (Exception e) {
            log.warn("[AiGlobalSetting] 写入字段 {} 失败: {}", dtoField, e.getMessage());
        }
    }
}
