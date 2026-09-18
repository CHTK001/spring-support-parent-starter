package com.chua.common.support.ai;

import java.util.Map;

/**
* 目标检测引擎配置（engine + 通用 期权 键值映射）。
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

    /**
    * 获取Engine
    *
    * @return 获取engine的结果
    */
    public String getEngine() { return engine; }
    /**
    * 设置Engine
    *
    * @param engine engine
    */
    public void setEngine(String engine) { this.engine = engine; }
    /**
    * 获取期权
    *
    * @return 获取期权的结果
    */
    public Map<String, Object> getOptions() { return options; }
    /**
    * 设置期权
    *
    * @param options 期权
    */
    public void setOptions(Map<String, Object> options) { this.options = options; }

    /**
    * @return 建造器实例
    */
    public static DetectionConfigurationBuilder builder() {
        /**
        * detection配置构建器。
        * @return detection配置构建器的结果
        */
        return new DetectionConfigurationBuilder();
    }

    /**
    * 转换为新的 构建器（保留当前字段值）。
    *
    * @return Builder 实例
    */
    public DetectionConfigurationBuilder toBuilder() {
        return DetectionConfiguration.builder()
                .engine(engine)
                .options(options);
    }

    /**
    * @return 选项映射（不为 空，永不为 空）
    */
    public Map<String, Object> option() {
        return options != null ? options : java.util.Collections.emptyMap();
    }

    /**
    * detection配置 建造器。
    * @author CH
    * @since 4.0.0
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

        /**
        * Engine
        *
        * @param engine engine
        * @return engine的结果
        */
        public DetectionConfigurationBuilder engine(String engine) { this.engine = engine; return this; }
        /**
        * 期权
        *
        * @param options 期权
        * @return 期权的结果
        */
        public DetectionConfigurationBuilder options(Map<String, Object> options) { this.options = options; return this; }

        /**
        * 构造 detection配置 实例。
        *
        * @return DetectionConfiguration 实例
        */
        public DetectionConfiguration build() {
            /**
            * detection配置。
            * @param engine engine
            * @param options 期权
            * @return detection配置的结果
            */
            return new DetectionConfiguration(engine, options);
        }
    }
}
