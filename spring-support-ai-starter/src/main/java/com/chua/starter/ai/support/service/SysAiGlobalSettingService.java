package com.chua.starter.ai.support.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SysAiGlobalSettingService {

    Optional<String> getValue(String group, String field);

    boolean getBoolean(String group, String field, boolean defaultValue);

    List<Map<String, Object>> list(String group);

    void replace(Object request);
}
