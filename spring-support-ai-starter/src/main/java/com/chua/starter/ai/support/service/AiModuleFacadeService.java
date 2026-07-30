package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.AiModuleQuery;
import com.chua.starter.ai.support.entity.SysAiModule;
import com.chua.starter.ai.support.entity.SysAiSetting;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface AiModuleFacadeService {

    List<SysAiModule> list(String moduleType, String moduleVendor);

    default List<SysAiModule> list(Map<String, Object> params) {
        return list(
                params != null ? (String) params.getOrDefault("type", null) : null,
                params != null ? (String) params.getOrDefault("vendor", null) : null
        );
    }

    SysAiModule getById(Integer id);

    SysAiModule save(SysAiModule module);

    boolean deleteById(Integer id);

    List<SysAiSetting> settings(Integer moduleId);

    default void saveSettings(Integer moduleId, List<SysAiSetting> settings) {
    }

    Map<String, Object> page(AiModuleQuery query);

    boolean update(SysAiModule entity);
}
