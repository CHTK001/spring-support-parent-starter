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
     * @param code 编码
     * @param name 名称
     * @param description description
     * @param type 类型
     * @param enabled 已启用
     */
    public LocalModuleDefinition(String code, String name, String description, String type, boolean enabled) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.type = type;
        this.enabled = enabled;
    }

    /**
     * 获取编码
     *
     * @return 获取编码的结果
     */
    public String getCode() { return code; }
    /**
     * 设置编码
     *
     * @param code 编码
     */
    public void setCode(String code) { this.code = code; }
    /**
     * 获取名称
     *
     * @return 获取名称的结果
     */
    public String getName() { return name; }
    /**
     * 设置名称
     *
     * @param name 名称
     */
    public void setName(String name) { this.name = name; }
    /**
     * 获取Description
     *
     * @return 获取description的结果
     */
    public String getDescription() { return description; }
    /**
     * 设置Description
     *
     * @param description description
     */
    public void setDescription(String description) { this.description = description; }
    /**
     * 获取类型
     *
     * @return 获取类型的结果
     */
    public String getType() { return type; }
    /**
     * 设置类型
     *
     * @param type 类型
     */
    public void setType(String type) { this.type = type; }
    /**
     * 是否已启用
     *
     * @return 是否已启用的结果
     */
    public boolean isEnabled() { return enabled; }
    /**
     * 设置已启用
     *
     * @param enabled 已启用
     */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * @return 建造器实例
     */
    public static LocalModuleDefinitionBuilder builder() {
        return new LocalModuleDefinitionBuilder();
    }

    /**
     * 本地moduledefinition 建造器。
     * @author CH
     * @since 4.0.0
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

        /**
         * 编码
         *
         * @param code 编码
         * @return 编码的结果
         */
        public LocalModuleDefinitionBuilder code(String code) {
            this.code = code;
            return this;
        }
        /**
         * 名称
         *
         * @param name 名称
         * @return 名称的结果
         */
        public LocalModuleDefinitionBuilder name(String name) {
            this.name = name;
            return this;
        }
        /**
         * Description
         *
         * @param description description
         * @return description的结果
         */
        public LocalModuleDefinitionBuilder description(String description) {
            this.description = description;
            return this;
        }
        /**
         * 类型
         *
         * @param type 类型
         * @return 类型的结果
         */
        public LocalModuleDefinitionBuilder type(String type) {
            this.type = type;
            return this;
        }
        /**
         * 已启用
         *
         * @param enabled 已启用
         * @return 已启用的结果
         */
        public LocalModuleDefinitionBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * 构造 本地moduledefinition 实例。
         *
         * @return LocalModuleDefinition 实例
         */
        public LocalModuleDefinition build() {
            return new LocalModuleDefinition(code, name, description, type, enabled);
        }
    }
}
