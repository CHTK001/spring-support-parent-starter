package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiModule;
import com.chua.starter.ai.support.model.AiModuleQuery;

import java.util.List;
import java.util.Map;

public interface AiSettingFacadeService {

    List<SysAiModule> listModuleSettings(Map<String, Object> params);

    List<SysAiModule> list(AiModuleQuery query);

    void saveModuleSettings(Integer moduleId, List<Map<String, Object>> settings);

    default void deleteByModuleId(Integer moduleId) {
    }
}
