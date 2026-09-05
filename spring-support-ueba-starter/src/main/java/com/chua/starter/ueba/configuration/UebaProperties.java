package com.chua.starter.ueba.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UEBA 配置属性。
 * <p>
 * 前缀为 {@code plugin.ueba}，控制 UEBA 分析引擎的自动装配行为：启用开关、
 * MiniMind 开关、配置文件来源与模型目录。未配置任何属性时使用默认值，
 * 引擎将加载 classpath 上的 {@code ueba-config.yaml} 并按内置加载策略定位模型。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.ueba")
public class UebaProperties {

    /**
     * 是否启用 UEBA 分析引擎
     */
    private boolean enabled = true;

    /**
     * 是否启用 MiniMind 语义解释（禁用时使用模板解释）
     */
    private boolean llm = true;

    /**
     * classpath 上的配置文件路径（当 configPath 为空时使用）
     */
    private String configResource = "ueba-config.yaml";

    /**
     * 外部配置文件路径（优先于 configResource）
     */
    private String configPath = "";

    /**
     * 模型目录（写入 ueba.model.dir，AutoEncoder 与 LSTM 模型均从该目录加载）
     */
    private String modelDir = "";
}