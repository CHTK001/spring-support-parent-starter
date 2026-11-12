package com.chua.starter.ai.support.service.settings;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.vector.VectorStorageDescriptor;
import com.chua.common.support.vector.VectorStorageField;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
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
     * 敏感字段回显掩码。
     *
     * <p>用户不修改凭据时前端会原样回传该值，服务端据此判定「保持原值」，
     * 因此真凭据不会被响应体带出去，也不会被掩码覆盖掉。</p>
     */
    private static final String MASK = "******";

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
        // enableCache（global.enable_cache）/ stream（global.stream）已移除：两者只在
        // DTO 与 FIELD_MAP 之间往返，全仓没有任何业务读取点（对话缓存与流式的真实开关
        // 是请求体的 stream 字段与各客户端自身实现）。保留它们等于让 PUT 成功、
        // GET 回显、实际什么都不会发生。
        m.put("systemPrompt", new String[]{"runtime", "system_prompt"});
        // 遗留别名 ragEmbeddingModel 原本指向已废弃的引擎字段 rag_embedding_model。
        // 改指模型字段 rag_embedding_model_name：既符合「只选模型不选引擎」的契约，
        // 也避免整包 PUT 把已删除的引擎字段重新写回数据库、让设置接口再次把它暴露出去。
        m.put("ragEmbeddingModel", new String[]{"rag", "rag_embedding_model_name"});
        m.put("ragLlmModel", new String[]{"rag", "rag_llm_model"});
        // ragOcrModel 已移除：OCR 统一走 ocr 分组（见 OcrClientManager），RAG 组不再单独配 OCR。
        m.put("ragUploadDir", new String[]{"rag", "rag_upload_dir"});
        m.put("ragVectorStorage", new String[]{"rag", "rag_vector_storage"});
        m.put("ragVectorStorageConfig", new String[]{"rag", "rag_vector_storage_config"});
        m.put("ragSystemPrompt", new String[]{"rag", "rag_system_prompt"});
        m.put("ragChunkSize", new String[]{"rag", "rag_chunk_size"});
        m.put("ragChunkOverlap", new String[]{"rag", "rag_chunk_overlap"});
        m.put("ragSplitter", new String[]{"rag", "rag_splitter"});
        // workshop 三项（storage_dir / max_upload_size_mb / allowed_extensions）已移除：
        // 没有对应的 GlobalSettingProvider 分组，也没有任何业务读取点，只在 DTO 里往返。
        m.put("protocolServerEnabled", new String[]{"runtime", "protocol_server_enabled"});        return java.util.Map.copyOf(m);
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
        dto.setSystemPrompt(getStr("systemPrompt"));
        dto.setRagEmbeddingModel(getStr("ragEmbeddingModel"));
        dto.setRagLlmModel(getStr("ragLlmModel"));
        dto.setRagUploadDir(getStr("ragUploadDir"));
        dto.setRagVectorStorage(getStr("ragVectorStorage"));
        dto.setRagVectorStorageConfig(readVectorStorageConfig());
        dto.setRagSystemPrompt(getStr("ragSystemPrompt"));
        dto.setRagChunkSize(getInt("ragChunkSize", null));
        dto.setRagChunkOverlap(getInt("ragChunkOverlap", null));
        dto.setRagSplitter(getStr("ragSplitter"));
        dto.setProtocolServerEnabled(getBool("protocolServerEnabled", null));
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
        writeVectorStorageConfig(setting.getRagVectorStorageConfig());
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
         * @param "protocolServerEnabled" "协议服务端已启用"
         * @param setting.getProtocolServerEnabled( setting.获取协议服务端已启用(
         */
        put("protocolServerEnabled", setting.getProtocolServerEnabled());
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

    // ==================== 向量库配置 ====================

    /**
     * 读取向量库配置，敏感字段以掩码返回。
     *
     * <p>凭据（Milvus token 等）不回显真实值，统一替换为 {@link #MASK}。
     * 前端把它当占位符展示，用户不改动时原样回传即视为「保持不变」。</p>
     *
     * @return SPI 名称到键值配置的映射；没有配置时返回空映射
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, java.util.Map<String, Object>> readVectorStorageConfig() {
        java.util.Map<String, java.util.Map<String, Object>> result = new java.util.LinkedHashMap<>();
        String json = getStr("ragVectorStorageConfig");
        if (json == null || json.isBlank()) {
            return result;
        }
        java.util.Map<String, Object> parsed;
        try {
            parsed = Json.fromJson(json);
        } catch (Exception e) {
            log.warn("[AiGlobalSetting] 解析向量库配置失败，按空配置返回: {}", e.getMessage());
            return result;
        }
        if (parsed == null) {
            return result;
        }
        for (java.util.Map.Entry<String, Object> storageEntry : parsed.entrySet()) {
            String storageName = storageEntry.getKey();
            if (!(storageEntry.getValue() instanceof java.util.Map<?, ?> rawFields)) {
                continue;
            }
            java.util.Set<String> sensitiveKeys = sensitiveKeys(storageName);
            java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> fieldEntry : rawFields.entrySet()) {
                if (fieldEntry.getKey() == null) {
                    continue;
                }
                String key = String.valueOf(fieldEntry.getKey());
                Object value = fieldEntry.getValue();
                if (sensitiveKeys.contains(key) && value != null && !String.valueOf(value).isBlank()) {
                    fields.put(key, MASK);
                } else {
                    fields.put(key, value);
                }
            }
            result.put(storageName, fields);
        }
        return result;
    }

    /**
     * 写入向量库配置。
     *
     * <p>安全约定：</p>
     * <ul>
     *   <li>只接受已注册 SPI 的名字，未知向量库直接拒绝，避免存进去之后 RAG 起不来；</li>
     *   <li>只接受该 SPI 描述器里声明的字段，未知字段直接拒绝，避免脏数据；</li>
     *   <li>敏感字段提交空值或掩码时保留库中原值，不会被清空；</li>
     *   <li>按「向量库 → 字段」两层增量合并，局部提交不会抹掉别的向量库配置。</li>
     * </ul>
     *
     * @param submitted 前端提交的向量库配置，可为 null（视为不改动）
     */
    private void writeVectorStorageConfig(java.util.Map<String, java.util.Map<String, Object>> submitted) {
        if (submitted == null) {
            return;
        }
        java.util.Map<String, java.util.Map<String, Object>> merged = unmask(submitted);
        if (merged.isEmpty()) {
            return;
        }
        String json = Json.toJson(merged);
        put("ragVectorStorageConfig", json);
    }

    /**
     * 按已保存值回填敏感字段，并把提交内容按两层结构合并进去。
     *
     * @param submitted 前端提交的配置
     * @return 合并后的完整配置
     */
    private java.util.Map<String, java.util.Map<String, Object>> unmask(
            java.util.Map<String, java.util.Map<String, Object>> submitted) {
        java.util.Map<String, java.util.Map<String, Object>> merged = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Map<String, Object>> stored = readRawVectorStorageConfig();
        java.util.Map<String, VectorStorageDescriptor> descriptors = descriptorIndex();
        for (java.util.Map.Entry<String, java.util.Map<String, Object>> storageEntry
                : submitted.entrySet()) {
            String storageName = storageEntry.getKey() == null ? null : storageEntry.getKey().trim();
            if (storageName == null || storageName.isEmpty()) {
                continue;
            }
            VectorStorageDescriptor descriptor = descriptors.get(storageName.toLowerCase());
            if (descriptor == null) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM,
                        "未注册的向量库: " + storageName + "，已注册: " + descriptors.keySet());
            }
            java.util.Map<String, Object> storedFields = stored.get(storageName);
            java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
            if (storedFields != null) {
                fields.putAll(storedFields);
            }
            java.util.Set<String> allowed = allowedKeys(descriptor);
            java.util.Set<String> sensitiveKeys = sensitiveKeys(storageName);
            java.util.Map<String, Object> submittedFields = storageEntry.getValue();
            if (submittedFields != null) {
                for (java.util.Map.Entry<String, Object> fieldEntry : submittedFields.entrySet()) {
                    String key = fieldEntry.getKey() == null ? null : fieldEntry.getKey().trim();
                    if (key == null || key.isEmpty()) {
                        continue;
                    }
                    if (!allowed.contains(key)) {
                        throw new ApiException(ApiErrorCode.INVALID_PARAM,
                                "向量库 " + storageName + " 不支持配置项: " + key + "，可用: " + allowed);
                    }
                    Object value = fieldEntry.getValue();
                    boolean unchanged = value == null
                            || String.valueOf(value).isBlank()
                            || MASK.equals(String.valueOf(value));
                    if (sensitiveKeys.contains(key) && unchanged) {
                        // 敏感字段留空即「不改」，保留原值，避免回显掩码把真凭据覆盖掉
                        continue;
                    }
                    fields.put(key, value);
                }
            }
            merged.put(storageName, fields);
        }
        return merged;
    }

    /**
     * 读取未脱敏的原始配置。
     *
     * @return SPI 名称到键值配置的映射
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, java.util.Map<String, Object>> readRawVectorStorageConfig() {
        java.util.Map<String, java.util.Map<String, Object>> result = new java.util.LinkedHashMap<>();
        String json = getStr("ragVectorStorageConfig");
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            java.util.Map<String, Object> parsed = Json.fromJson(json);
            if (parsed == null) {
                return result;
            }
            for (java.util.Map.Entry<String, Object> storageEntry : parsed.entrySet()) {
                if (storageEntry.getKey() == null
                        || !(storageEntry.getValue() instanceof java.util.Map<?, ?> rawFields)) {
                    continue;
                }
                java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
                for (java.util.Map.Entry<?, ?> fieldEntry : rawFields.entrySet()) {
                    if (fieldEntry.getKey() != null) {
                        fields.put(String.valueOf(fieldEntry.getKey()), fieldEntry.getValue());
                    }
                }
                result.put(storageEntry.getKey(), fields);
            }
        } catch (Exception e) {
            log.warn("[AiGlobalSetting] 读取向量库原始配置失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 构造「SPI 名称（小写）到描述器」的索引。
     *
     * @return 描述器索引
     */
    private java.util.Map<String, VectorStorageDescriptor> descriptorIndex() {
        java.util.Map<String, VectorStorageDescriptor> index = new java.util.LinkedHashMap<>();
        try {
            for (VectorStorageDescriptor descriptor : VectorStorageProvider.descriptors()) {
                if (descriptor != null && descriptor.name() != null) {
                    index.put(descriptor.name().toLowerCase(), descriptor);
                }
            }
        } catch (Exception e) {
            log.warn("[AiGlobalSetting] 枚举向量库描述器失败: {}", e.getMessage());
        }
        return index;
    }

    /**
     * 取出某个向量库描述器里允许配置的字段名。
     *
     * @param descriptor 向量库描述器
     * @return 允许的字段名集合
     */
    private java.util.Set<String> allowedKeys(VectorStorageDescriptor descriptor) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (VectorStorageField field : descriptor.fields()) {
            keys.add(field.key());
        }
        return keys;
    }

    /**
     * 取出某个向量库里标记为敏感的字段名。
     *
     * @param storageName 向量库名称
     * @return 敏感字段名集合
     */
    private java.util.Set<String> sensitiveKeys(String storageName) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        VectorStorageDescriptor descriptor = descriptorIndex().get(
                storageName == null ? "" : storageName.toLowerCase());
        if (descriptor == null) {
            return keys;
        }
        for (VectorStorageField field : descriptor.fields()) {
            if (field.secret()) {
                keys.add(field.key());
            }
        }
        return keys;
    }

    // ==================== 写入 ====================

    /**
     * 放入。
     * @param dtoField dto字段
     * @param value 值
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
