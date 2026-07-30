package com.chua.common.support.ai;

public class LocalModuleDefinition {

    private String code;
    private String name;
    private String description;
    private String type;
    private boolean enabled;

    public LocalModuleDefinition() {
    }

    public LocalModuleDefinition(String code, String name, String description, String type, boolean enabled) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.type = type;
        this.enabled = enabled;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public static LocalModuleDefinitionBuilder builder() {
        return new LocalModuleDefinitionBuilder();
    }

    public static class LocalModuleDefinitionBuilder {
        private String code;
        private String name;
        private String description;
        private String type;
        private boolean enabled;

        public LocalModuleDefinitionBuilder code(String code) { this.code = code; return this; }
        public LocalModuleDefinitionBuilder name(String name) { this.name = name; return this; }
        public LocalModuleDefinitionBuilder description(String description) { this.description = description; return this; }
        public LocalModuleDefinitionBuilder type(String type) { this.type = type; return this; }
        public LocalModuleDefinitionBuilder enabled(boolean enabled) { this.enabled = enabled; return this; }

        public LocalModuleDefinition build() {
            return new LocalModuleDefinition(code, name, description, type, enabled);
        }
    }
}
