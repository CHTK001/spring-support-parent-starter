package com.chua.common.support.ai;

/**
 * 本地模块定义（编码、名称、描述、类型与启用标志）。
 *
 * @since 4.0.0
 * @author CH
 */
public class LocalModuleDefinition {

    /**
     * 模块编码（唯一标识）
     */
    private String code;

    /**
     * 模块显示名
     */
    private String name;

    /**
     * 模块描述
     */
    private String description;

    /**
     * 模块类型
     */
    private String type;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 默认构造。
     */
    public LocalModuleDefinition() {
    }

    /**
     * 全参构造。
     */
    public LocalModuleDefinition(String code, String name, String description, String type, boolean enabled) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.type = type;
        this.enabled = enabled;
    }

    /** 获取Code */
    public String getCode() { return code; }
    /** 设置Code */
    public void setCode(String code) { this.code = code; }
    /** 获取Name */
    public String getName() { return name; }
    /** 设置Name */
    public void setName(String name) { this.name = name; }
    /** 获取Description */
    public String getDescription() { return description; }
    /** 设置Description */
    public void setDescription(String description) { this.description = description; }
    /** 获取Type */
    public String getType() { return type; }
    /** 设置Type */
    public void setType(String type) { this.type = type; }
    /** 是否Enabled */
    public boolean isEnabled() { return enabled; }
    /** 设置Enabled */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * @return 建造器实例
     */
    public static LocalModuleDefinitionBuilder builder() {
        return new LocalModuleDefinitionBuilder();
    }

    /**
     * LocalModuleDefinition 建造器。
     */
    public static class LocalModuleDefinitionBuilder {

        /**
         * 模块编码
         */
        private String code;

        /**
         * 模块显示名
         */
        private String name;

        /**
         * 模块描述
         */
        private String description;

        /**
         * 模块类型
         */
        private String type;

        /**
         * 是否启用
         */
        private boolean enabled;

        /** Code */
        public LocalModuleDefinitionBuilder code(String code) { this.code = code; return this; }
        /** Name */
        public LocalModuleDefinitionBuilder name(String name) { this.name = name; return this; }
        /** Description */
        public LocalModuleDefinitionBuilder description(String description) { this.description = description; return this; }
        /** Type */
        public LocalModuleDefinitionBuilder type(String type) { this.type = type; return this; }
        /** Enabled */
        public LocalModuleDefinitionBuilder enabled(boolean enabled) { this.enabled = enabled; return this; }

        /**
         * 构造 LocalModuleDefinition 实例。
         *
         * @return LocalModuleDefinition 实例
         */
        public LocalModuleDefinition build() {
            return new LocalModuleDefinition(code, name, description, type, enabled);
        }
    }
}
