package com.chua.common.support.ai;

import java.util.List;
import java.util.Map;

public interface AiTool {

    String name();

    String description();

    List<String> parameters();

    AiToolResult call(Map<String, Object> args);
}
