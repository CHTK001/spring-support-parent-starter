package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiGlobalSettingDTO;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
* 全局设置服务实现（单条记录 标识=1 的语义，落地到 {@code sys_ai_global_setting} 键值表）。
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
    * 键值全局设置服务（读写 sys_AI_全局_setting 表）
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
    * DTO 字段与 (分组, 表字段) 的映射。
    * 无 SPI 对应字段的配置项统一落到 {@code global} 分组，保持往返一致。
     */
    private static final java.util.Map<String, String[]> FIELD_MAP = fieldMap();

    /**
    * 字段映射。
    * @return 字段映射的结果
     */
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
        m.put("ragEmbeddingModel", new String[]{"rag", "rag_embedding_model"});
        m.put("ragLlmModel", new String[]{"rag", "rag_llm_model"});
        m.put("ragOcrModel", new String[]{"rag", "rag_ocr_model"});
        m.put("ragUploadDir", new String[]{"rag", "rag_upload_dir"});
        m.put("ragVectorStorage", new String[]{"rag", "rag_vector_storage"});
        m.put("ragSystemPrompt", new String[]{"rag", "rag_system_prompt"});
        m.put("ragChunkSize", new String[]{"rag", "rag_chunk_size"});
        m.put("ragChunkOverlap", new String[]{"rag", "rag_chunk_overlap"});
        m.put("ragSplitter", new String[]{"rag", "rag_splitter"});
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
        /**
        * 放入。
        * @param "chatClientEnabled" "对话客户端已启用"
        * @param setting.getChatClientEnabled( setting.获取对话客户端已启用(
         */
        put("chatClientEnabled", setting.getChatClientEnabled());
        /**
        * 放入。
        * @param "enableUsageAnalysis" "enableusageanalysis"
        * @param setting.getEnableUsageAnalysis( setting.获取enableusageanalysis(
         */
        put("enableUsageAnalysis", setting.getEnableUsageAnalysis());
        /**
        * 放入。
        * @param "usageReportInterval" "usagereport间隔"
        * @param setting.getUsageReportInterval( setting.获取usagereport间隔(
         */
        put("usageReportInterval", setting.getUsageReportInterval());
        /**
        * 放入。
        * @param "defaultModel" "默认模型"
        * @param setting.getDefaultModel( setting.获取默认模型(
         */
        put("defaultModel", setting.getDefaultModel());
        /**
        * 放入。
        * @param "temperature" "temperature"
        * @param setting.getTemperature( setting.获取temperature(
         */
        put("temperature", setting.getTemperature());
        /**
        * 放入。
        * @param "maxTokens" "最大令牌"
        * @param setting.getMaxTokens( setting.获取最大令牌(
         */
        put("maxTokens", setting.getMaxTokens());
        /**
        * 放入。
        * @param "timeout" "超时"
        * @param setting.getTimeout( setting.获取超时(
         */
        put("timeout", setting.getTimeout());
        /**
        * 放入。
        * @param "enableCache" "enable缓存"
        * @param setting.getEnableCache( setting.获取enable缓存(
         */
        put("enableCache", setting.getEnableCache());
        /**
        * 放入。
        * @param "stream" "流"
        * @param setting.getStream( setting.获取流(
         */
        put("stream", setting.getStream());
        /**
        * 放入。
        * @param "systemPrompt" "系统提示符"
        * @param setting.getSystemPrompt( setting.获取系统提示符(
         */
        put("systemPrompt", setting.getSystemPrompt());
        /**
        * 放入。
        * @param "ragEmbeddingModel" "rag嵌入模型"
        * @param setting.getRagEmbeddingModel( setting.getrag嵌入模型(
         */
        put("ragEmbeddingModel", setting.getRagEmbeddingModel());
        /**
        * 放入。
        * @param "ragLlmModel" "ragllm模型"
        * @param setting.getRagLlmModel( setting.获取ragllm模型(
         */
        put("ragLlmModel", setting.getRagLlmModel());
        /**
        * 放入。
        * @param "ragOcrModel" "ragocr模型"
        * @param setting.getRagOcrModel( setting.获取ragocr模型(
         */
        put("ragOcrModel", setting.getRagOcrModel());
        /**
        * 放入。
        * @param "ragUploadDir" "raguploaddir"
        * @param setting.getRagUploadDir( setting.获取raguploaddir(
         */
        put("ragUploadDir", setting.getRagUploadDir());
        /**
        * 放入。
        * @param "ragVectorStorage" "rag向量storage"
        * @param setting.getRagVectorStorage( setting.getrag向量storage(
         */
        put("ragVectorStorage", setting.getRagVectorStorage());
        /**
        * 放入。
        * @param "ragSystemPrompt" "rag系统提示符"
        * @param setting.getRagSystemPrompt( setting.getrag系统提示符(
         */
        put("ragSystemPrompt", setting.getRagSystemPrompt());
        /**
        * 放入。
        * @param "ragChunkSize" "ragchunk大小"
        * @param setting.getRagChunkSize( setting.获取ragchunk大小(
         */
        put("ragChunkSize", setting.getRagChunkSize());
        /**
        * 放入。
        * @param "ragChunkOverlap" "ragchunkoverlap"
        * @param setting.getRagChunkOverlap( setting.获取ragchunkoverlap(
         */
        put("ragChunkOverlap", setting.getRagChunkOverlap());
        /**
        * 放入。
        * @param "ragSplitter" "ragsplitter"
        * @param setting.getRagSplitter( setting.获取ragsplitter(
         */
        put("ragSplitter", setting.getRagSplitter());
        /**
        * 放入。
        * @param "workshopStorageDir" "workshopstoragedir"
        * @param setting.getWorkshopStorageDir( setting.获取workshopstoragedir(
         */
        put("workshopStorageDir", setting.getWorkshopStorageDir());
        /**
        * 放入。
        * @param "workshopMaxUploadSizeMb" "workshop最大upload大小mb"
        * @param setting.getWorkshopMaxUploadSizeMb( setting.getworkshop最大upload大小mb(
         */
        put("workshopMaxUploadSizeMb", setting.getWorkshopMaxUploadSizeMb());
        /**
        * 放入。
        * @param "workshopAllowedExtensions" "workshopallowed延伸"
        * @param setting.getWorkshopAllowedExtensions( setting.获取workshopallowed延伸(
         */
        put("workshopAllowedExtensions", setting.getWorkshopAllowedExtensions());
        /**
        * 放入。
        * @param "protocolServerEnabled" "协议服务端已启用"
        * @param setting.getProtocolServerEnabled( setting.获取协议服务端已启用(
         */
        put("protocolServerEnabled", setting.getProtocolServerEnabled());
        /**
        * 放入。
        * @param "protocolServerPort" "协议服务端端口"
        * @param setting.getProtocolServerPort( setting.获取协议服务端端口(
         */
        put("protocolServerPort", setting.getProtocolServerPort());
        return get();
    }

    // ==================== 读取 ====================

    /**
    * 获取str。
    * @param dtoField dto字段
    * @return 获取str的结果
     */
    private String getStr(String dtoField) {
        String[] groupField = FIELD_MAP.get(dtoField);
        if (groupField == null) {
            return null;
        }
        return sysAiGlobalSettingService.getValue(groupField[0], groupField[1]).orElse(null);
    }

    /**
    * 获取bool。
    * @param dtoField dto字段
    * @param def def
    * @return 获取bool的结果
     */
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

    /**
    * 获取int。
    * @param dtoField dto字段
    * @param def def
    * @return 获取int的结果
     */
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

    /**
    * 获取decimal。
    * @param dtoField dto字段
    * @param def def
    * @return 获取decimal的结果
     */
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
            /**
            * bigdecimal。
            * @param opt.get( opt.获取(
            * @return BigDecimal的结果
             */
            return new BigDecimal(opt.get().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ==================== 写入 ====================

    /**
    * 放入。
    * @param dtoField dto字段
    * @param value 价值观
     */
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
