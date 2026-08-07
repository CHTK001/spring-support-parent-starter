package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.AiModuleQuery;
import com.chua.starter.ai.support.entity.SysAiModule;
import com.chua.starter.ai.support.entity.SysAiSetting;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 模块门面服务接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface AiModuleFacadeService {

    /**
     * 按类型与厂商查询模块列表。
     *
     * @param moduleType   模块类型
     * @param moduleVendor 模块厂商
     * @return 模块列表
     */
    List<SysAiModule> list(String moduleType, String moduleVendor);

    /**
     * 按查询参数查询模块列表。
     *
     * @param params 查询参数
     * @return 模块列表
     */
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
