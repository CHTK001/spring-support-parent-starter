package com.chua.common.support.ai;

import java.util.Map;

public class DetectionConfiguration {

    private String engine;
    private Map<String, Object> options;

    public DetectionConfiguration() {
    }

    public DetectionConfiguration(String engine, Map<String, Object> options) {
        this.engine = engine;
        this.options = options;
    }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }

    public static DetectionConfigurationBuilder builder() {
        return new DetectionConfigurationBuilder();
    }

    public DetectionConfigurationBuilder toBuilder() {
        return DetectionConfiguration.builder()
                .engine(engine)
                .options(options);
    }

    public Map<String, Object> option() {
        return options != null ? options : java.util.Collections.emptyMap();
    }

    public static class DetectionConfigurationBuilder {
        private String engine;
        private Map<String, Object> options;

        public DetectionConfigurationBuilder engine(String engine) { this.engine = engine; return this; }
        public DetectionConfigurationBuilder options(Map<String, Object> options) { this.options = options; return this; }

        public DetectionConfiguration build() {
            return new DetectionConfiguration(engine, options);
        }
    }
}
