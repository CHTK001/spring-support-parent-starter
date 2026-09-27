package com.chua.starter.ai.support.configuration.client;

import com.chua.deeplearning.support.engine.ModelParams;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.model.HardwareConfig;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ONNX 模型级运行参数服务。
 *
 * <p>把 {@link ModelParams}（进程内存储）中的模型级推理参数持久化到
 * {@code sys_ai_global_setting} 表，并在应用启动时回填，使配置在重启后依然生效。</p>
 *
 * <p>存储约定：</p>
 * <ul>
 *   <li>group 固定为 {@code onnx-model}；</li>
 *   <li>field 固定为 {@code params:模型标识}，value 为 JSON 对象字符串
 *       （形如 {@code {"device":"gpu","threshold":0.5}}）；</li>
 *   <li>一个模型一行，便于按模型精确读写与删除。</li>
 * </ul>
 *
 * <p>参数语义：{@code device}（cpu/gpu/auto）为通用项；其余键由各 Translator
 * 自行声明读取（阈值、inputSize、candidates 等），本服务不做白名单限制。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnnxModelParamService {

    /**
     * 全局设置分组。
     */
    public static final String GROUP = "onnx-model";

    /**
     * 字段前缀。
     */
    public static final String FIELD_PREFIX = "params:";

    /**
     * 通用参数键：推理设备。
     */
    public static final String KEY_DEVICE = "device";

    /**
     * 通用参数键：置信度阈值。
     */
    public static final String KEY_THRESHOLD = "threshold";

    /**
     * 通用参数键：NMS 阈值。
     */
    public static final String KEY_NMS = "iouThreshold";

    /**
     * 通用参数键：线程数。
     */
    public static final String KEY_THREADS = "threads";

    /**
     * 设备可选值。
     */
    public static final List<String> DEVICE_OPTIONS = List.of("auto", "cpu", "gpu");

    /**
     * 全局设置读取工具。
     */
    private final GlobalSettingReader settingReader;

    /**
     * 全局设置写入服务（负责落库并刷新缓存）。
     */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * JSON 序列化工具。
     */
    private final ObjectMapper objectMapper;

    /**
     * 应用就绪后回填全部模型级参数。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreOnStartup() {
        try {
            ModelRegistry.discoverAll();
            int restored = 0;
            for (String modelId : ModelRegistry.getAll().stream().map(ModelRegistry.Entry::modelId).toList()) {
                if (loadInto(modelId)) {
                    restored++;
                }
            }
            log.info("[OnnxModelParam] 启动回填模型参数 {} 个", restored);
        } catch (Exception e) {
            log.warn("[OnnxModelParam] 启动回填模型参数失败: {}", e.getMessage());
        }
    }

    /**
     * 读取某模型的参数（来自数据库，不含运行时未持久化的值）。
     *
     * @param modelId 模型标识
     * @return 参数表；无配置时返回 空
     */
    public Map<String, Object> load(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Map.of();
        }
        String raw = settingReader.getStr(GROUP, fieldOf(modelId));
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("[OnnxModelParam] 参数解析失败 model={} raw={}", modelId, raw);
            return Map.of();
        }
    }

    /**
     * 从数据库读取并写入 {@link ModelParams}。
     *
     * @param modelId 模型标识
     * @return true 表示该模型存在持久化参数并已回填
     */
    public boolean loadInto(String modelId) {
        Map<String, Object> params = load(modelId);
        if (params.isEmpty()) {
            return false;
        }
        ModelParams.put(modelId, params);
        return true;
    }

    /**
     * 读取某模型当前生效的参数（合并进程内存储）。
     *
     * @param modelId 模型标识
     * @return 参数表
     */
    public Map<String, Object> current(String modelId) {
        return new LinkedHashMap<>(ModelParams.get(modelId));
    }

    /**
     * 写入并持久化某模型的参数。
     *
     * @param modelId 模型标识
     * @param params  参数表
     * @return 实际保存的参数
     */
    public Map<String, Object> save(String modelId, Map<String, Object> params) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("模型标识不能为空");
        }
        Map<String, Object> cleaned = sanitize(params);
        if (cleaned.isEmpty()) {
            return reset(modelId);
        }
        ModelParams.put(modelId, cleaned);
        persist(modelId, cleaned);
        return cleaned;
    }

    /**
     * 合并写入并持久化某模型的参数。
     *
     * @param modelId 模型标识
     * @param params  待合并参数
     * @return 合并后的参数
     */
    public Map<String, Object> merge(String modelId, Map<String, Object> params) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("模型标识不能为空");
        }
        Map<String, Object> merged = new LinkedHashMap<>(current(modelId));
        merged.putAll(sanitize(params));
        if (merged.isEmpty()) {
            return reset(modelId);
        }
        ModelParams.put(modelId, merged);
        persist(modelId, merged);
        return merged;
    }

    /**
     * 清除某模型的参数（内存与数据库同时清除）。
     *
     * @param modelId 模型标识
     * @return 空参数表
     */
    public Map<String, Object> reset(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Map.of();
        }
        ModelParams.remove(modelId);
        try {
            globalSettingService.upsertValue(GROUP, fieldOf(modelId), "", "ONNX 模型级运行参数（JSON）");
        } catch (Exception e) {
            log.warn("[OnnxModelParam] 参数清除失败 model={}: {}", modelId, e.getMessage());
        }
        return Map.of();
    }

    /**
     * 列出所有已持久化参数的模型标识。
     *
     * @return 模型标识列表
     */
    public List<String> configuredModels() {
        return new ArrayList<>(ModelParams.getAllKeys());
    }

    /**
     * 查询可用的参数键说明，供前端渲染表单。
     *
     * <p>通用键固定返回；模型自有键由调用方按模型补充。</p>
     *
     * @return 键 → 说明
     */
    public Map<String, String> commonParamSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put(KEY_DEVICE, "推理设备：" + String.join(" / ", DEVICE_OPTIONS)
                + "；auto 表示按本机驱动与 CUDA 原生库自动判定");
        schema.put(KEY_THRESHOLD, "置信度阈值（0~1），留空使用模型默认值");
        schema.put(KEY_NMS, "NMS IOU 阈值（0~1），留空使用模型默认值");
        schema.put(KEY_THREADS, "推理线程数，留空使用运行时默认");
        return schema;
    }

    /**
     * 查询某模型注册信息中的硬件建议（供前端提示）。
     *
     * @param modelId 模型标识
     * @return 硬件配置；无注册信息时返回 空
     */
    public HardwareConfig hardwareOf(String modelId) {
        ModelRegistry.Entry entry = ModelRegistry.get(modelId);
        return entry == null ? null : entry.hardwareConfig();
    }

    /**
     * 清洗参数：剔除空键、空值与非法设备值。
     *
     * @param params 原始参数
     * @return 清洗后的参数
     */
    private Map<String, Object> sanitize(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> item : params.entrySet()) {
            String key = item.getKey();
            Object value = item.getValue();
            if (key == null || key.isBlank() || value == null) {
                continue;
            }
            if (value instanceof String text) {
                if (text.isBlank()) {
                    continue;
                }
                if (KEY_DEVICE.equals(key)) {
                    String device = text.trim().toLowerCase();
                    if (!DEVICE_OPTIONS.contains(device)) {
                        log.warn("[OnnxModelParam] 忽略非法 device 值 key={} device={}", key, text);
                        continue;
                    }
                    cleaned.put(key, device);
                    continue;
                }
            }
            cleaned.put(key, value);
        }
        return cleaned;
    }

    /**
     * 持久化参数到全局设置表。
     *
     * @param modelId 模型标识
     * @param params  参数表
     */
    private void persist(String modelId, Map<String, Object> params) {
        try {
            String json = objectMapper.writeValueAsString(params);
            globalSettingService.upsertValue(GROUP, fieldOf(modelId), json, "ONNX 模型级运行参数（JSON）");
            log.info("[OnnxModelParam] 已保存模型参数 model={} params={}", modelId, json);
        } catch (Exception e) {
            throw new IllegalStateException("模型参数保存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构造字段名。
     *
     * @param modelId 模型标识
     * @return 字段名
     */
    private static String fieldOf(String modelId) {
        return FIELD_PREFIX + modelId;
    }

    /**
     * 允许的参数键集合（供校验使用）。
     *
     * @return 键集合
     */
    public Set<String> commonKeys() {
        return commonParamSchema().keySet();
    }
}
