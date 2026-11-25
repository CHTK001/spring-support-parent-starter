package com.chua.starter.ueba.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UEBA 配置属性。
 * <p>
 * 前缀为 {@code plugin.ueba}，控制 UEBA 分析引擎的自动装配行为：启用开关、
 * minimind 开关、配置文件来源与模型目录。未配置任何属性时使用默认值，
 * 引擎将加载 类路径 上的 {@code ueba-config.yaml} 并按内置加载策略定位模型。
 * </p>
 *
 * <p>完整 YAML 配置示例（前缀 {@code plugin.ueba}）：</p>
 * <pre>
 * plugin:
 *   ueba:
 *     enabled: true                        # 总开关，false 时整个自动配置跳过
 *     llm: true                            # MiniMind 语义解释开关，false 时用模板解释
 *     config-resource: ueba-config.yaml    # classpath 引擎配置文件（config-path 为空时生效）
 *     #config-path: D:/config/ueba-config.yaml   # 外部引擎配置文件，优先于 config-resource
 *     #model-dir: D:/models/ueba           # ONNX 模型目录，写入系统属性 ueba.model.dir
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.ueba")
public class UebaProperties {

    /**
     * 是否启用 UEBA 分析引擎（装配总开关）。
     * <p>含义：{@code true} 时装配 {@code UebaEngine} 与服务信息贡献者；
     * {@code false} 时整个自动配置跳过，容器内不存在相关 Bean。</p>
     * <p>默认值：{@code true}（未配置时视为开启，对应 {@code matchIfMissing = true}）。</p>
     * <pre>
     * plugin:
     *   ueba:
     *     enabled: true
     * </pre>
     */
    private boolean enabled = true;

    /**
     * 是否启用 minimind 语义解释（禁用时使用模板解释）。
     * <p>含义：{@code true} 时引擎调用 {@code enableLlm()} 装配 MiniMind 分析器，
     * 仍需 JVM 系统属性 {@code ueba.minimind.model.dir} 提供模型目录，缺失或加载失败时
     * 运行期自动回退模板解释；{@code false} 时调用 {@code disableLlm()}，直接使用内置模板解释。</p>
     * <p>默认值：{@code true}。</p>
     * <pre>
     * plugin:
     *   ueba:
     *     llm: true
     * </pre>
     */
    private boolean llm = true;

    /**
     * 类路径 上的配置文件路径（当 配置路径 为空时使用）。
     * <p>含义：引擎配置文件（{@code features} / {@code autoEncoder} / {@code lstm} / {@code risk} /
     * {@code preprocessing} 段）的 classpath 资源位置，仅当 {@link #configPath} 为空时生效。</p>
     * <p>默认值：{@code ueba-config.yaml}。</p>
     * <pre>
     * plugin:
     *   ueba:
     *     config-resource: ueba-config.yaml
     * </pre>
     */
    private String configResource = "ueba-config.yaml";

    /**
     * 外部配置文件路径（优先于 configResource）。
     * <p>含义：外部引擎配置文件的路径，非空时以文件方式加载并优先于 {@link #configResource}。</p>
     * <p>默认值：空串 {@code ""}；为空或未配置时回落到 {@link #configResource} 指定的 classpath 资源。</p>
     * <pre>
     * plugin:
     *   ueba:
     *     config-path: D:/config/ueba-config.yaml
     * </pre>
     */
    private String configPath = "";

    /**
     * 模型目录（写入系统属性 ueba.model.dir，AutoEncoder 与 LSTM 模型均从该目录加载）。
     * <p>含义：AutoEncoder 与 LSTM 的 ONNX 模型目录；非空时由 {@code UebaEngineBuilder#build()}
     * 写入 JVM 系统属性 {@code ueba.model.dir}（JVM 全局副作用，后写覆盖先写）。</p>
     * <p>默认值：空串 {@code ""}；为空或未配置时不写该系统属性，按引擎内置策略定位模型。</p>
     * <pre>
     * plugin:
     *   ueba:
     *     model-dir: D:/models/ueba
     * </pre>
     */
    private String modelDir = "";
}
