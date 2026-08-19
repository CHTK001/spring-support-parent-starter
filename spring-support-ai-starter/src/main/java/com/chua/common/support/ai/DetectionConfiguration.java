package com.chua.common.support.ai;

import java.util.Map;

/**
 * 目标检测引擎配置（engine + 通用 options 键值映射）。
 *
 * @since 4.0.0
 * @author CH
 */
public class DetectionConfiguration {

    /**
     * 引擎名称（如 yolo / dnn）
     */
    private String engine;

    /**
     * 引擎选项键值映射
     */
    private Map<String, Object> options;

    /**
     * 默认构造。
     */
    public DetectionConfiguration() {
    }

    /**
     * 全参构造。
     *
     * @param engine  引擎名称
     * @param options 引擎选项
     */
    public DetectionConfiguration(String engine, Map<String, Object> options) {
        this.engine = engine;
        this.options = options;
    }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }

    /**
     * @return 建造器实例
     */
    public static DetectionConfigurationBuilder builder() {
        return new DetectionConfigurationBuilder();
    }

    /**
     * 转换为新的 Builder（保留当前字段值）。
     *
     * @return Builder 实例
     */
    public DetectionConfigurationBuilder toBuilder() {
        return DetectionConfiguration.builder()
                .engine(engine)
                .options(options);
    }

    /**
     * @return 选项映射（不为 null，永不为 null）
     */
    public Map<String, Object> option() {
        return options != null ? options : java.util.Collections.emptyMap();
    }

    /**
     * DetectionConfiguration 建造器。
     */
    public static class DetectionConfigurationBuilder {

        /**
         * 引擎名称
         */
        private String engine;

        /**
         * 引擎选项
         */
        private Map<String, Object> options;

        public DetectionConfigurationBuilder engine(String engine) { this.engine = engine; return this; }
        public DetectionConfigurationBuilder options(Map<String, Object> options) { this.options = options; return this; }

        /**
         * 构造 DetectionConfiguration 实例。
         *
         * @return DetectionConfiguration 实例
         */
        public DetectionConfiguration build() {
            return new DetectionConfiguration(engine, options);
        }
    }
}
