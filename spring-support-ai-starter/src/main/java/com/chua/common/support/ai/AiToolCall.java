package com.chua.common.support.ai;

import java.io.Serializable;
import java.util.Map;

public class AiToolCall implements Serializable {

    private String id;
    private String name;
    private Map<String, Object> arguments;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
