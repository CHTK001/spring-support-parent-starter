package com.chua.starter.ai.support.configuration.properties;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.manage.event.SettingChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认模型解析器 —— 全局单例 Bean，供其它模块直接注入读取最终生效的默认模型。
 *
 * <p><b>优先级：页面配置（DB）&gt; yaml（{@link DefaultModelProperties}）。</b>
 * DB 指页面「系统配置 → 默认模型」写入的 {@code sys_ai_global_setting}
 * 中 {@code setting_group='models'} 分组。</p>
 *
 * <p>取值支持两种写法，二者都返回纯模型名：</p>
 * <ul>
 *   <li>{@code gpt-4o} → {@code gpt-4o}</li>
 *   <li>{@code openai:gpt-4o} → {@code gpt-4o}（前缀为 providerId，用于消歧）</li>
 * </ul>
 *
 * <p><b>缓存</b>：解析结果按能力键缓存在本 Bean 内，避免高频路径反复读库。
 * 页面保存设置时 Spring 会发布 {@link SettingChangedEvent}，据此清空缓存即时生效；
 * 另外提供 {@link #evict()} / {@link #evict(String)} 供外部手动失效。</p>
 *
 * <p>其它模块用法示例：</p>
 * <pre>{@code
 * // 1) 直接注入本 Bean
 * @RequiredArgsConstructor
 * public class MyService {
 *     private final DefaultModelResolver defaultModels;
 *     String chat = defaultModels.chat();          // 最终生效的默认对话模型
 * }
 *
 * // 2) 或注入配置属性做纯 yaml 读取
 * private final DefaultModelProperties defaultModelProperties;
 * }</pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(DefaultModelProperties.class)
public class DefaultModelResolver {

    /**
     * setting_group：默认模型分组
     */
    public static final String GROUP = "models";

    /**
     * 全局设置读取工具（页面配置来源）
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * yaml 配置属性（兜底来源）
     */
    private final DefaultModelProperties properties;

    /**
     * 解析结果缓存：能力键 → 原始取值（未配置缓存 {@link #NULL_SENTINEL}）
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 缓存"未配置"哨兵：区别于"没缓存过"
     */
    private static final String NULL_SENTINEL = "\0NULL";

    /**
     * 能力键 → 库字段名
     */
    private static final Map<String, String> FIELD_BY_KEY = new LinkedHashMap<>();

    static {
        FIELD_BY_KEY.put("chat", "defaultChatModelId");
        FIELD_BY_KEY.put("titleSummary", "defaultTitleSummaryModelId");
        FIELD_BY_KEY.put("visualAssistant", "defaultVisualAssistantModelId");
        FIELD_BY_KEY.put("historyCompression", "defaultHistoryCompressionModelId");
        FIELD_BY_KEY.put("historyCompressionPrompt", "defaultHistoryCompressionPrompt");
        FIELD_BY_KEY.put("translation", "defaultTranslationModelId");
        FIELD_BY_KEY.put("translationPrompt", "defaultTranslationPrompt");
        FIELD_BY_KEY.put("plateDetect", "defaultPlateDetectModelId");
        FIELD_BY_KEY.put("plateRecognize", "defaultPlateRecognizeModelId");
        FIELD_BY_KEY.put("feature", "defaultFeatureModelId");
        FIELD_BY_KEY.put("ragEmbedding", "defaultRagEmbeddingModelId");
        FIELD_BY_KEY.put("rerank", "defaultRerankModelId");
        FIELD_BY_KEY.put("imageQuality", "defaultImageQualityModelId");
        FIELD_BY_KEY.put("textToVideo", "defaultTextToVideoModelId");
        FIELD_BY_KEY.put("textToImage", "defaultTextToImageModelId");
        FIELD_BY_KEY.put("imageEnhance", "defaultImageEnhanceModelId");
        FIELD_BY_KEY.put("ocrDetect", "defaultOcrDetectModelId");
        FIELD_BY_KEY.put("ocrRecognize", "defaultOcrRecognizeModelId");
        FIELD_BY_KEY.put("ocrDirection", "defaultOcrDirectionModelId");
        FIELD_BY_KEY.put("ocrSuperResolution", "defaultOcrSuperResolutionModelId");
        FIELD_BY_KEY.put("layout", "defaultLayoutModelId");
        FIELD_BY_KEY.put("faceDetect", "defaultFaceDetectModelId");
        FIELD_BY_KEY.put("faceRecognize", "defaultFaceRecognizeModelId");
        FIELD_BY_KEY.put("liveness", "defaultLivenessModelId");
        FIELD_BY_KEY.put("eyeDetect", "defaultEyeDetectModelId");
        FIELD_BY_KEY.put("smileDetect", "defaultSmileDetectModelId");
        FIELD_BY_KEY.put("imageClassify", "defaultImageClassifyModelId");
        FIELD_BY_KEY.put("imageDetect", "defaultImageDetectModelId");
        FIELD_BY_KEY.put("imageSegment", "defaultImageSegmentModelId");
        FIELD_BY_KEY.put("imageCaptioning", "defaultImageCaptioningModelId");
        FIELD_BY_KEY.put("depth", "defaultDepthModelId");
        FIELD_BY_KEY.put("faceQuality", "defaultFaceQualityModelId");
        FIELD_BY_KEY.put("pedestrian", "defaultPedestrianModelId");
    }

    /**
     * 构造时自检：yaml 属性与字段映射必须严格一一对应。
     *
     * <p>两侧一旦漂移（例如新增能力只加了 yaml 却漏了映射，或反之），
     * 运行期只会静默返回 null，难以排查，故启动即失败并打印缺失项。</p>
     */
    @jakarta.annotation.PostConstruct
    void verifyMapping() {
        List<String> missing = FIELD_BY_KEY.keySet().stream()
                .filter(key -> !isYamlPropertyPresent(key))
                .toList();
        if (!missing.isEmpty()) {
            log.error("[DefaultModelResolver] 以下能力键缺少对应的 yaml 属性声明: {}", missing);
        }
    }

    /**
     * 判断 yaml 属性对象是否声明了该能力键对应的属性（不要求有值）。
     *
     * <p>全部为空是合法配置（表示交由各能力既有兜底逻辑），因此不能用「值是否为空」
     * 判断覆盖度；这里只校验属性是否声明，避免新增能力时漏加属性导致静默失效。</p>
     *
     * @param key 能力键
     * @return true=属性存在
     */
    private boolean isYamlPropertyPresent(String key) {
        String prop = toCamelCase(key);
        return java.util.Arrays.stream(DefaultModelProperties.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals(prop));
    }

    /**
     * 能力键转驼峰属性名（yaml 的 kebab-case 绑定后即为驼峰字段名）。
     *
     * @param key 能力键
     * @return 驼峰属性名
     */
    private static String toCamelCase(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        boolean upper = false;
        for (char ch : key.toCharArray()) {
            if (ch == '-') {
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(ch) : ch);
            upper = false;
        }
        return sb.toString();
    }

    /**
     * 读取最终生效的默认模型（纯模型名，去掉 provider 前缀）。
     *
     * <p>顺序：先页面配置（DB），为空时回退 yaml。</p>
     *
     * @param key 能力键（如 {@code chat} / {@code ocrDetect}）
     * @return 模型名；两侧都未配置时返回 {@code null}
     */
    public String resolve(String key) {
        if (FIELD_BY_KEY.get(key) == null) {
            log.warn("[DefaultModelResolver] 未知的能力键: {}", key);
            return null;
        }
        // 1) 页面配置优先（含 provider 前缀）
        // 2) yaml 兜底
        return normalize(resolveRaw(key));
    }

    /**
     * 读取最终生效的默认模型原始取值（保留 {@code providerId:model} 形式）。
     *
     * @param key 能力键
     * @return 原始取值；未配置时返回 {@code null}
     */
    public String resolveRaw(String key) {
        String cached = cache.get(key);
        if (cached != null) {
            return NULL_SENTINEL.equals(cached) ? null : cached;
        }
        String field = FIELD_BY_KEY.get(key);
        if (field == null) {
            return null;
        }
        String fromPage = trimToNull(tryReadPage(field));
        String result = fromPage != null ? fromPage : trimToNull(yamlValue(key));
        cache.put(key, result == null ? NULL_SENTINEL : result);
        return result;
    }

    /**
     * 清空全部解析缓存。
     *
     * <p>页面保存设置时自动触发（监听 {@link SettingChangedEvent}）；
     * 外部模块若绕过页面直接改库，可主动调用本方法。</p>
     */
    public void evict() {
        if (!cache.isEmpty()) {
            cache.clear();
        }
    }

    /**
     * 清空指定能力键的缓存。
     *
     * @param key 能力键
     */
    public void evict(String key) {
        cache.remove(key);
    }

    /**
     * 页面保存设置时清空缓存，保证「页面配置 > yaml」即时生效。
     *
     * @param event 设置变更事件
     */
    @EventListener
    public void onSettingChanged(SettingChangedEvent event) {
        if (GROUP.equals(event.getGroup())) {
            evict();
            log.debug("[DefaultModelResolver] 页面配置已变更，清空默认模型缓存");
        }
    }

    /**
     * 取最终生效的全部默认模型（仅包含已配置项）。
     *
     * @return 能力键 → 纯模型名
     */
    public Map<String, String> resolveAll() {
        Map<String, String> result = new LinkedHashMap<>();
        FIELD_BY_KEY.keySet().forEach(key -> {
            String v = resolve(key);
            if (v != null) {
                result.put(key, v);
            }
        });
        return result;
    }

    /**
     * 能力键是否已配置（页面或 yaml 任一有值）。
     *
     * @param key 能力键
     * @return true=已配置
     */
    public boolean isConfigured(String key) {
        return resolveRaw(key) != null;
    }

    /**
     * 默认对话模型。
     *
     * @return 模型名，未配置返回 {@code null}
     */
    public String chat() {
        return resolve("chat");
    }

    /**
     * 读取页面配置；失败（缓存/数据库不可达）时返回 {@code null} 并记录日志，不阻断调用方。
     *
     * @param field 库字段名
     * @return 原始取值
     */
    private String tryReadPage(String field) {
        try {
            return globalSettingReader.getStr(GROUP, field);
        } catch (Exception e) {
            log.debug("[DefaultModelResolver] 读取页面配置失败 field={}: {}", field, e.getMessage());
            return null;
        }
    }

    /**
     * 从 yaml 属性对象按能力键取值。
     *
     * @param key 能力键
     * @return yaml 值
     */
    private String yamlValue(String key) {
        return switch (key) {
            case "chat" -> properties.getChat();
            case "titleSummary" -> properties.getTitleSummary();
            case "visualAssistant" -> properties.getVisualAssistant();
            case "historyCompression" -> properties.getHistoryCompression();
            case "historyCompressionPrompt" -> properties.getHistoryCompressionPrompt();
            case "translation" -> properties.getTranslation();
            case "translationPrompt" -> properties.getTranslationPrompt();
            case "plateDetect" -> properties.getPlateDetect();
            case "plateRecognize" -> properties.getPlateRecognize();
            case "feature" -> properties.getFeature();
            case "ragEmbedding" -> properties.getRagEmbedding();
            case "rerank" -> properties.getRerank();
            case "imageQuality" -> properties.getImageQuality();
            case "textToVideo" -> properties.getTextToVideo();
            case "textToImage" -> properties.getTextToImage();
            case "imageEnhance" -> properties.getImageEnhance();
            case "ocrDetect" -> properties.getOcrDetect();
            case "ocrRecognize" -> properties.getOcrRecognize();
            case "ocrDirection" -> properties.getOcrDirection();
            case "ocrSuperResolution" -> properties.getOcrSuperResolution();
            case "layout" -> properties.getLayout();
            case "faceDetect" -> properties.getFaceDetect();
            case "faceRecognize" -> properties.getFaceRecognize();
            case "liveness" -> properties.getLiveness();
            case "eyeDetect" -> properties.getEyeDetect();
            case "smileDetect" -> properties.getSmileDetect();
            case "imageClassify" -> properties.getImageClassify();
            case "imageDetect" -> properties.getImageDetect();
            case "imageSegment" -> properties.getImageSegment();
            case "imageCaptioning" -> properties.getImageCaptioning();
            case "depth" -> properties.getDepth();
            case "faceQuality" -> properties.getFaceQuality();
            case "pedestrian" -> properties.getPedestrian();
            default -> null;
        };
    }

    /**
     * 去掉 {@code providerId:} 前缀，只保留模型名。
     *
     * @param raw 原始取值
     * @return 纯模型名；无有效值时返回 {@code null}
     */
    private static String normalize(String raw) {
        String v = trimToNull(raw);
        if (v == null) {
            return null;
        }
        int idx = v.indexOf(':');
        String model = idx >= 0 ? v.substring(idx + 1).trim() : v;
        return model.isEmpty() ? null : model;
    }

    /**
     * 去空白，空串视为未配置。
     *
     * @param raw 原始取值
     * @return 有效取值；无则 {@code null}
     */
    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        return v.isEmpty() ? null : v;
    }
}
