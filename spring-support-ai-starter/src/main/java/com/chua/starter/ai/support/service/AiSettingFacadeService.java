package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiModule;
import com.chua.starter.ai.support.model.AiModuleQuery;

import java.util.List;
import java.util.Map;

/**
 * AI 设置门面服务接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface AiSettingFacadeService {

    /**
     * 查询模块设置列表。
     *
     * @param params 查询参数
     * @return 模块列表
     */
    List<SysAiModule> listModuleSettings(Map<String, Object> params);

    /**
     * 按查询条件查询模块。
     *
     * @param query 查询条件
     * @return 模块列表
     */
    List<SysAiModule> list(AiModuleQuery query);

    /**
     * 保存模块设置。
     *
     * @param moduleId 模块 ID
     * @param settings 设置项列表
     */
    void saveModuleSettings(Integer moduleId, List<Map<String, Object>> settings);

    /**
     * 按模块 ID 删除设置。
     *
     * @param moduleId 模块 ID
     */
    default void deleteByModuleId(Integer moduleId) {
    }
}
