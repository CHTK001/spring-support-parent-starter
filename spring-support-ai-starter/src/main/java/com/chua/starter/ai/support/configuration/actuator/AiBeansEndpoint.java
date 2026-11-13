package com.chua.starter.ai.support.configuration.actuator;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.runtime.AgentProviderFactory;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 客户端 Bean 端点：{@code GET /actuator/ai-beans}。
 *
 * <p>展示当前进程里真正装配了哪些客户端/管线，以及各自用的模型：对话（默认客户端 / 聚合 /
 * provider 列表）、Agent 引擎解析结果，以及人脸 / 重识别 / RAG / OCR 的模型配置。</p>
 *
 * <p><b>只读端点不能有副作用</b>：人脸 / 重识别 / RAG 的 {@code describe()} 会触发管线构建，
 * 在缺少 OpenCV native 的环境（如 glibc 2.17 的集群）会抛 {@code UnsatisfiedLinkError}，
 * 让整个端点 500。这里改为只读配置字段，不实例化任何管线；每一段都单独兜底。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Endpoint(id = "ai-beans")
@RequiredArgsConstructor
public class AiBeansEndpoint {

    /**
     * 对话客户端注册中心
     */
    private final ChatClientRegistry chatClientRegistry;

    /**
     * Agent 引擎工厂（opencode-server / agentscope 解析与降级）
     */
    private final AgentProviderFactory agentProviderFactory;

    /**
     * 配置读取（只读，不触发客户端构建）
     */
    private final GlobalSettingReader settingReader;

    /**
     * 人脸相关配置字段
     */
    private static final List<String> FACE_FIELDS = List.of(
            "detector_model", "feature_model", "liveness_model", "quality_model", "device");

    /**
     * 重识别相关配置字段
     */
    private static final List<String> REID_FIELDS = List.of(
            "reid_feature_model", "reid_feature_dimension", "reid_threshold",
            "reid_match_threshold", "track_threshold", "default_camera_id");

    /**
     * RAG 相关配置字段
     */
    private static final List<String> RAG_FIELDS = List.of(
            "rag_llm_model", "rag_embedding_model_name", "rag_vector_storage", "rag_splitter");

    /**
     * OCR 相关配置字段
     */
    private static final List<String> OCR_FIELDS = List.of(
            "detector_model", "recognizer_model", "direction_model", "device");

    /**
     * 读取当前 Bean 摘要。
     *
     * @return chat / agent / face / reid / rag / ocr
     */
    @ReadOperation
    public Map<String, Object> read() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chat", safe(this::chat));
        result.put("agent", safe(this::agent));
        result.put("face", safe(() -> settings("face", FACE_FIELDS)));
        result.put("reid", safe(() -> settings("face", REID_FIELDS)));
        result.put("rag", safe(() -> settings("rag", RAG_FIELDS)));
        result.put("ocr", safe(() -> settings("ocr", OCR_FIELDS)));
        return result;
    }

    /**
     * 对话客户端摘要。
     *
     * @return 默认客户端、聚合状态、provider / model 列表
     */
    private Map<String, Object> chat() {
        ChatClient defaultClient = chatClientRegistry.getDefault();
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("clientCount", chatClientRegistry.size());
        chat.put("aggregate", chatClientRegistry.getAggregate() != null);
        chat.put("defaultType", defaultClient == null ? null : defaultClient.getClass().getSimpleName());
        chat.put("defaultModel", defaultClient == null ? null : defaultClient.getModel());
        chat.put("providers", chatClientRegistry.getProviders());
        chat.put("models", chatClientRegistry.getModels());
        return chat;
    }

    /**
     * Agent 引擎摘要。
     *
     * @return selected / requested / effective / degraded 等
     */
    private Map<String, Object> agent() {
        Map<String, Object> agent = new LinkedHashMap<>();
        AgentProviderFactory.RuntimeResolution resolution = agentProviderFactory.resolution();
        if (resolution == null) {
            agent.put("effective", agentProviderFactory.resolveProvider());
            return agent;
        }
        agent.put("selected", resolution.selected());
        agent.put("requested", resolution.requested());
        agent.put("effective", resolution.effective());
        agent.put("degraded", resolution.degraded());
        agent.put("fallback", resolution.fallback());
        agent.put("reason", resolution.reason());
        agent.put("preparing", resolution.preparing());
        return agent;
    }

    /**
     * 读取某个分组下的指定字段（空值给空串，避免 JSON 里出现空洞）。
     *
     * @param group  分组
     * @param fields 字段列表
     * @return field → value
     */
    private Map<String, Object> settings(String group, List<String> fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : fields) {
            String value = settingReader.getStr(group, field);
            values.put(field, value == null ? "" : value);
        }
        return values;
    }

    /**
     * 单段兜底：任何一段失败只影响该段，不让整个端点 500。
     *
     * @param supplier 数据提供者
     * @return 数据；失败时为 {"error": "..."}
     */
    private Map<String, Object> safe(Supplier<Map<String, Object>> supplier) {
        try {
            return supplier.get();
        } catch (Throwable ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return error;
        }
    }
}
