package com.chua.starter.ai.support.generate;

import com.chua.starter.ai.support.manage.store.SysAiProviderModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 模型级「生成参数」配置的读写契约。
 *
 * <p>生成能力（文生图 / 文生视频）除提示词外还需要一批模型特有约束：是否支持参考图、
 * 单次生成张数上限、可用分辨率列表、视频时长列表、推理步数区间等。这些随模型变化，
 * 因此不写死在前端，而是随模型行走 {@code sys_ai_provider_model_capabilities} 的
 * {@code generate} 节点（复用已有 JSON 列，不新增数据库字段）。</p>
 *
 * <p>存储形态：</p>
 * <pre>{@code
 * capabilities: [
 *   {
 *     "task": "image_generation",
 *     "traits": ["input:text", "output:image"],
 *     "generate": {
 *       "referenceImage": true,
 *       "maxCount": 4,
 *       "sizes": ["1024x1024", "1024x1792", "1792x1024"],
 *       "durations": [5, 10],
 *       "negativePrompt": true,
 *       "seed": true,
 *       "steps": { "min": 1, "max": 50, "default": 30 }
 *     }
 *   }
 * ]}</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class GenerateConfigSupport {

    /**
     * 生成任务类型：图像
     */
    public static final String TYPE_IMAGE = "image";

    /**
     * 生成任务类型：视频
     */
    public static final String TYPE_VIDEO = "video";

    /**
     * 默认单次生成张数上限
     */
    public static final int DEFAULT_MAX_COUNT = 1;

    /**
     * 默认分辨率
     */
    public static final String DEFAULT_SIZE = "1024x1024";

    /**
     * 无参考图
     */
    private static final List<String> EMPTY_SIZES = List.of();

    /**
     * 工具类不允许实例化
     */
    private GenerateConfigSupport() {
    }

    /**
     * 生成参数视图：供接口返回与前端渲染
     *
     * @param referenceImage  是否支持参考图
     * @param negativePrompt  是否支持负面提示词
     * @param seed            是否支持随机种子
     * @param maxCount        单次生成张数上限
     * @param sizes           可用分辨率列表
     * @param durations       可用视频时长（秒）
     * @param stepsMin        推理步数下限
     * @param stepsMax        推理步数上限
     * @param stepsDefault    推理步数默认值
     * @param styles          模型支持的风格 key 列表；为空表示沿用前端的默认风格表
     * @param style           是否支持 style 参数；false 时不下发该参数
     */
    public record View(
            boolean referenceImage,
            boolean negativePrompt,
            boolean seed,
            int maxCount,
            List<String> sizes,
            List<Integer> durations,
            Integer stepsMin,
            Integer stepsMax,
            Integer stepsDefault,
            List<String> styles,
            boolean style
    ) {
        /**
         * 规范构造器：对分辨率列表与时长列表做防御性拷贝。
         *
         * <p>value class 前置条件——集合组件必须深不可变；两个列表均由本类工厂方法产出，
         * 保证非空且元素非 空，故可使用 {@code List.copyOf}。</p>
         *
         * @param referenceImage 是否支持参考图
         * @param negativePrompt 是否支持负面提示词
         * @param seed           是否支持随机种子
         * @param maxCount       单次生成张数上限
         * @param sizes          可用分辨率列表
         * @param durations      可用视频时长（秒）
         * @param stepsMin       推理步数下限
         * @param stepsMax       推理步数上限
         * @param stepsDefault   推理步数默认值
         * @param styles         模型支持的风格 key 列表；为空表示沿用前端的默认风格表
         * @param style          是否支持 style 参数；false 时不下发该参数
         *                       （如 Agnes 会直接拒绝），前端也应隐藏风格选择
         */
        public View {
            sizes = List.copyOf(Objects.requireNonNull(sizes, "sizes 不能为 null"));
            durations = List.copyOf(Objects.requireNonNull(durations, "durations 不能为 null"));
            styles = List.copyOf(Objects.requireNonNull(styles, "styles 不能为 null"));
        }
    }

    /**
     * 从模型行读取生成参数；未配置时返回按类型给出的默认视图。
     *
     * @param modelCfg 模型行，可为空
     * @param type     任务类型（image / video）
     * @return 生成参数视图，永不为 null
     */
    public static View read(SysAiProviderModel modelCfg, String type) {
        Map<String, Object> gen = findGenerateNode(modelCfg);
        boolean video = TYPE_VIDEO.equalsIgnoreCase(type);
        int maxCount = intOrDefault(gen, "maxCount", video ? 1 : DEFAULT_MAX_COUNT);
        List<String> sizes = sizes(gen);
        if (sizes.isEmpty()) {
            sizes = video ? EMPTY_SIZES : List.of(DEFAULT_SIZE);
        }
        return new View(
                boolOrDefault(gen, "referenceImage", false),
                boolOrDefault(gen, "negativePrompt", true),
                boolOrDefault(gen, "seed", true),
                Math.max(1, maxCount),
                sizes,
                intList(gen.get("durations")),
                intOrNull(gen, "stepsMin"),
                intOrNull(gen, "stepsMax"),
                intOrNull(gen, "stepsDefault"),
                stringList(gen.get("styles")),
                // 默认 true：风格是「拼进提示词的描述」，不依赖上游参数，
                // 因此对任何模型都可用；只有模型显式声明不支持时才关闭。
                boolOrDefault(gen, "style", true)
        );
    }

    /**
     * 把生成参数写回模型行的 {@code capabilities[0].generate}。
     *
     * <p>只更新 {@code generate} 节点，{@code task} / {@code traits} 由调用方保证不变。
     * capabilities 为空时会新建一条记录。</p>
     *
     * @param modelCfg 模型行（原地修改）
     * @param view     待写入的生成参数
     */
    @SuppressWarnings("unchecked")
    public static void write(SysAiProviderModel modelCfg, View view) {
        if (modelCfg == null || view == null) {
            return;
        }
        List<Map<String, Object>> caps = modelCfg.getCapabilities();
        Map<String, Object> node;
        if (caps == null || caps.isEmpty()) {
            caps = new ArrayList<>(1);
            node = new java.util.LinkedHashMap<>(8);
            caps.add(node);
            modelCfg.setCapabilities(caps);
        } else {
            node = caps.getFirst();
            if (node == null) {
                node = new java.util.LinkedHashMap<>(8);
                caps.set(0, node);
            }
        }
        Map<String, Object> gen = new java.util.LinkedHashMap<>(12);
        gen.put("referenceImage", view.referenceImage());
        gen.put("negativePrompt", view.negativePrompt());
        gen.put("seed", view.seed());
        gen.put("maxCount", view.maxCount());
        gen.put("sizes", view.sizes());
        if (view.durations() != null && !view.durations().isEmpty()) {
            gen.put("durations", view.durations());
        }
        // 只在声明了风格时才写回：空列表表示该模型不支持 style 参数
        gen.put("style", view.style());
        if (view.styles() != null && !view.styles().isEmpty()) {
            gen.put("styles", view.styles());
        }
        if (view.stepsMin() != null) {
            gen.put("stepsMin", view.stepsMin());
        }
        if (view.stepsMax() != null) {
            gen.put("stepsMax", view.stepsMax());
        }
        if (view.stepsDefault() != null) {
            gen.put("stepsDefault", view.stepsDefault());
        }
        node.put("generate", gen);
    }

    /**
     * 定位 capabilities 中的 generate 节点。
     *
     * @param modelCfg 模型行，可为空
     * @return generate 节点；未配置返回空 Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> findGenerateNode(SysAiProviderModel modelCfg) {
        List<Map<String, Object>> caps = modelCfg == null ? null : modelCfg.getCapabilities();
        if (caps == null || caps.isEmpty()) {
            return Map.of();
        }
        for (Map<String, Object> cap : caps) {
            if (cap == null) {
                continue;
            }
            Object gen = cap.get("generate");
            if (gen instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        }
        return Map.of();
    }

    /**
     * 读取字符串列表并去重保序。
     *
     * @param gen generate 节点
     * @return 分辨率列表，永不为 null
     */
    private static List<String> sizes(Map<String, Object> gen) {
        Object raw = gen.get("sizes");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        Set<String> set = new LinkedHashSet<>(list.size());
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String s = item.toString().trim();
            if (!s.isEmpty()) {
                set.add(s);
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 读取整数列表。
     *
     * @param raw 原始值
     * @return 整数列表，未配置返回空列表
     */
    private static List<Integer> intList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>(list.size());
        for (Object item : list) {
            Integer v = intOrNull(item);
            if (v != null) {
                result.add(v);
            }
        }
        return result;
    }

    /**
     * 读取字符串列表（去空、去重、保序）。
     *
     * @param raw 原始值
     * @return 字符串列表，未配置返回空列表
     */
    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        Set<String> set = new LinkedHashSet<>(list.size());
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String s = item.toString().trim();
            if (!s.isEmpty()) {
                set.add(s);
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 宽松整数解析。
     *
     * @param value 原始值
     * @return 整数；无法解析返回 null
     */
    private static Integer intOrNull(Object value) {
        if (value == null || value == "") {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取节点中的整数键。
     *
     * @param map 节点，可为空
     * @param key 键
     * @return 整数；未配置或无法解析返回 null
     */
    private static Integer intOrNull(Map<String, Object> map, String key) {
        return map == null ? null : intOrNull(map.get(key));
    }

    /**
     * 带默认值的整数读取。
     *
     * @param map         节点
     * @param key         键
     * @param defaultValue 默认值
     * @return 整数或默认值
     */
    private static int intOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Integer v = intOrNull(map, key);
        return v == null ? defaultValue : v;
    }

    /**
     * 宽松布尔解析。
     *
     * @param map         节点
     * @param key         键
     * @param defaultValue 默认值
     * @return 布尔值
     */
    private static boolean boolOrDefault(Map<String, Object> map, String key, boolean defaultValue) {
        Object v = map.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(v.toString().trim());
    }
}
