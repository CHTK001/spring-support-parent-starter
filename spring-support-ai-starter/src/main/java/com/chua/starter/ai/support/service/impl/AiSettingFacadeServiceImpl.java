package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiModule;
import com.chua.starter.ai.support.entity.SysAiSetting;
import com.chua.starter.ai.support.mapper.SysAiSettingMapper;
import com.chua.starter.ai.support.model.AiModuleQuery;
import com.chua.starter.ai.support.service.AiSettingFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 模块设置门面服务实现。
 * <p>
 * 负责 sys_ai_setting 表的 CRUD 操作，
 * 支持按模块 ID 批量替换配置项。
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSettingFacadeServiceImpl implements AiSettingFacadeService {

    /** SYSAI设置mapper */
    private final SysAiSettingMapper sysAiSettingMapper;
    /** Modulefacadehelper */
    private final SysAiModuleFacadeHelper moduleFacadeHelper;

    /**
     * 查询条件辅助 —— 避免模块循环依赖，通过松耦合帮助类间接获取模块列表。
     */
    @org.springframework.stereotype.Component
    @RequiredArgsConstructor
    static class SysAiModuleFacadeHelper {

        @org.springframework.context.annotation.Lazy
        @org.springframework.beans.factory.annotation.Autowired
        /** Modulefacade */
        private AiModuleFacadeServiceImpl moduleFacade;

        List<SysAiModule> listModules(Map<String, Object> params) {
            if (moduleFacade == null) {
                return Collections.emptyList();
            }
            try {
                return moduleFacade.list(params);
            } catch (Exception e) {
                log.warn("[AiSettingFacadeHelper] 查询模块列表失败", e);
                return Collections.emptyList();
            }
        }

        List<SysAiModule> listModules(AiModuleQuery query) {
            if (moduleFacade == null) {
                return Collections.emptyList();
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> pageResult = moduleFacade.page(query);
                return (List<SysAiModule>) pageResult.getOrDefault("records", Collections.emptyList());
            } catch (Exception e) {
                log.warn("[AiSettingFacadeHelper] 分页查询模块列表失败", e);
                return Collections.emptyList();
            }
        }
    }

    @Override
    /** ListModuleSettings */
    public List<SysAiModule> listModuleSettings(Map<String, Object> params) {
        List<SysAiModule> modules = moduleFacadeHelper.listModules(params);
        for (SysAiModule module : modules) {
            List<SysAiSetting> settings = querySettingsByModuleId(module.getSysAiModuleId());
            module.setSysAiSettings(settings);
        }
        return modules;
    }

    @Override
    /** List */
    public List<SysAiModule> list(AiModuleQuery query) {
        List<SysAiModule> modules = moduleFacadeHelper.listModules(query);
        for (SysAiModule module : modules) {
            List<SysAiSetting> settings = querySettingsByModuleId(module.getSysAiModuleId());
            module.setSysAiSettings(settings);
        }
        return modules;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 保存ModuleSettings */
    public void saveModuleSettings(Integer moduleId, List<Map<String, Object>> settings) {
        if (moduleId == null) {
            log.warn("[AiSettingFacade] saveModuleSettings moduleId 为空");
            return;
        }

        // 先删除该模块下的所有旧配置
        LambdaQueryWrapper<SysAiSetting> deleteWrapper = Wrappers.<SysAiSetting>lambdaQuery()
                .eq(SysAiSetting::getSysAiSettingModuleId, moduleId);
        sysAiSettingMapper.delete(deleteWrapper);

        // 再插入新配置
        if (settings == null || settings.isEmpty()) {
            return;
        }

        int sort = 0;
        for (Map<String, Object> item : settings) {
            SysAiSetting setting = new SysAiSetting();
            setting.setSysAiSettingModuleId(moduleId);
            setting.setSysAiSettingKey((String) item.getOrDefault("key", ""));
            setting.setSysAiSettingValue(Objects.toString(item.get("value"), ""));
            setting.setSysAiSettingValueType((String) item.getOrDefault("valueType", "STRING"));
            setting.setSysAiSettingDescription((String) item.getOrDefault("description", ""));
            setting.setSysAiSettingSort(item.get("sort") instanceof Number
                    ? ((Number) item.get("sort")).intValue() : sort++);
            setting.setSysAiSettingStatus(item.get("status") instanceof Number
                    ? ((Number) item.get("status")).intValue() : 1);
            setting.setCreateTime(LocalDateTime.now());
            setting.setUpdateTime(LocalDateTime.now());
            sysAiSettingMapper.insert(setting);
        }

        log.info("[AiSettingFacade] 模块 {} 设置已更新，共 {} 项", moduleId, settings.size());
    }

    @Override
    /** 删除ByModuleId */
    public void deleteByModuleId(Integer moduleId) {
        if (moduleId == null) {
            return;
        }
        LambdaQueryWrapper<SysAiSetting> deleteWrapper = Wrappers.<SysAiSetting>lambdaQuery()
                .eq(SysAiSetting::getSysAiSettingModuleId, moduleId);
        int deleted = sysAiSettingMapper.delete(deleteWrapper);
        log.info("[AiSettingFacade] 删除模块 {} 的设置，共 {} 项", moduleId, deleted);
    }

    /**
     * 查询指定模块的配置项列表，按 sort 排序。
     */
    private List<SysAiSetting> querySettingsByModuleId(Integer moduleId) {
        LambdaQueryWrapper<SysAiSetting> wrapper = Wrappers.<SysAiSetting>lambdaQuery()
                .eq(SysAiSetting::getSysAiSettingModuleId, moduleId)
                .eq(SysAiSetting::getSysAiSettingStatus, 1)
                .orderByAsc(SysAiSetting::getSysAiSettingSort);
        return sysAiSettingMapper.selectList(wrapper);
    }
}
