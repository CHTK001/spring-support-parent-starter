package com.chua.starter.ai.support.configuration.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * YAML 全局设置覆盖层 —— {@code plugin.ai.settings.<group>.<field>}。
 *
 * <p><b>解决的问题：</b>{@code sys_ai_global_setting} 的键值（layout / ocr / face … 分组）
 * 只能通过设置页改，运维想「换一条 layout/OCR 管线」必须登页面操作、且散落在各环境的
 * 数据库里不可版本化。本类把同一批键开放到 application yaml：</p>
 *
 * <pre>
 * plugin:
 *   ai:
 *     settings:
 *       layout:
 *         layout-threshold: "0.42"   # 与 sys_ai_global_setting 的 layout_threshold 同键
 *         layout-model: "onnx:pp-doc-layout"
 *       ocr:
 *         recognizer-model: "onnx:paddleocrv6-rec"
 * </pre>
 *
 * <p><b>为什么是「扫 Environment 原始属性名」而不是 {@code @ConfigurationProperties}：</b>
 * 嵌套 Map 走绑定器有两处静默陷阱 ——
 * ① record 构造绑定按<b>参数名</b>寻址（{@code plugin.ai.settings.groups.…}，多一层且反直觉）；
 * ② 属性名会被适配为 {@code ConfigurationPropertyName}，snake 键里的下划线命运取决于
 * 适配规则，一旦被改写成点号就<b>整棵子树绑空且不报错</b> —— 运维改完 yaml 以为换成了
 * 管线、实际还在跑旧值，这比启动报错危险得多。直接扫原始属性名两处都不沾：
 * 键怎么写就怎么读，snake（与 DB field 逐字一致）/kebab 都由本类的归一化兜住。</p>
 *
 * <p><b>生效语义（部署级钦定）：</b>命中（非 null）即<b>优先于</b>用户覆盖与数据库 ——
 * 与既有 {@code plugin.ai.default-model}（页面 &gt; yaml 兜底）刻意相反：default-model 是
 * 「给无页面模块兜底」，本覆盖层是「部署方显式接管」，改 yaml 重启即换管线；
 * 设置页对同名键的修改在 yaml 键存在期间不生效（未出现在 yaml 的键，链路与从前完全一致）。</p>
 *
 * <p><b>取值约定：</b>键写了但值为 null（{@code layout-model:} 空挂）视同未配置，
 * 回落 用户覆盖 &gt; 数据库；显式空串（{@code layout-model: ""}）视为「强制清空」，
 * 消费方按空值走「未配置」分支 —— 与用户覆盖行的显式清空语义一致。</p>
 *
 * <p><b>范围：</b>构造期一次性扫描（Environment 此后只读），只认
 * yaml / properties 文件与 {@code --plugin.ai.settings.*} 命令行参数；
 * 环境变量形式（{@code CHUA_AI_SETTINGS_…}，下划线会被映射成分隔符）不承诺语义，别用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Component
public class AiSettingsYamlProperties {

    /**
     * 扫描前缀（带尾点；大小写不敏感匹配，但环境变量式下划线写法不构成点号故天然不命中）
     */
    private static final String PREFIX = "plugin.ai.settings.";

    /**
     * 归一化后的覆盖表：组 → （字段 → 值）；仅含显式配置了值的键（空挂不入表）
     */
    private final Map<String, Map<String, String>> groups;

    /**
     * 构造期扫描 Environment 中 {@code plugin.ai.settings.*} 的原始属性名。
     * 属性源按优先级序（高到低）遍历，同键先到先得，与 Environment 取值优先级一致。
     *
     * @param environment Spring 环境（含 yaml / properties / 命令行属性源）
     */
    @Autowired
    public AiSettingsYamlProperties(ConfigurableEnvironment environment) {
        Map<String, Map<String, String>> scanned = new LinkedHashMap<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (name == null || !name.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
                    continue;
                }
                String remainder = name.substring(PREFIX.length());
                int dot = remainder.indexOf('.');
                if (dot <= 0 || dot == remainder.length() - 1) {
                    // 没有「组.字段」结构（如 plugin.ai.settings.layout 单段）：忽略
                    continue;
                }
                Object raw = enumerable.getProperty(name);
                if (raw == null) {
                    // 空挂（有键无值）= 未配置，不入表（回落数据库链）
                    continue;
                }
                String group = normalize(remainder.substring(0, dot));
                String field = normalize(remainder.substring(dot + 1));
                Map<String, String> inner = scanned.computeIfAbsent(group, key -> new LinkedHashMap<>());
                inner.putIfAbsent(field, String.valueOf(raw).trim());
            }
        }
        this.groups = scanned;
    }

    /**
     * 读取覆盖值。
     *
     * @param group 分组（与数据库 group 列同名，如 layout / ocr；大小写与 kebab/snake 等价）
     * @param field 字段名（与数据库 field 列同名，如 layout_model；大小写与 kebab/snake 等价）
     * @return 生效值：yaml 显式配置时返回该值（可为显式空串）；未配置返回 {@code null}
     */
    public String get(String group, String field) {
        if (group == null || field == null) {
            return null;
        }
        Map<String, String> inner = groups.get(normalize(group));
        if (inner == null) {
            return null;
        }
        return inner.get(normalize(field));
    }

    /**
     * 是否存在任何覆盖键。
     *
     * @return true 表示 yaml 完全没配覆盖层
     */
    public boolean isEmpty() {
        return groups.isEmpty();
    }

    /**
     * 覆盖清单摘要（启动日志用）。
     *
     * @return 形如 {@code layout=[layout_model, device], ocr=[recognizer_model]}
     */
    public String summary() {
        if (isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> group : groups.entrySet()) {
            if (group.getValue().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(group.getKey()).append('=').append(group.getValue().keySet());
        }
        return sb.toString();
    }

    /**
     * 键归一化：去首尾空白 + 小写 + kebab 转 snake ——
     * 使 DB 的 {@code layout_model}、yaml 的 {@code layout-model}、
     * 大小写变体全部等价命中同一个键。
     *
     * @param key 原始键
     * @return 归一化键
     */
    private static String normalize(String key) {
        return key.trim().toLowerCase().replace('-', '_');
    }
}