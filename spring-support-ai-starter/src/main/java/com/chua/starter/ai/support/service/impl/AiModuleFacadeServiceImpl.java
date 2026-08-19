package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.ai.LocalModuleDefinition;
import com.chua.common.support.ai.LocalModuleProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.entity.SysAiModule;
import com.chua.starter.ai.support.entity.SysAiSetting;
import com.chua.starter.ai.support.mapper.AiModuleMapper;
import com.chua.starter.ai.support.mapper.SysAiSettingMapper;
import com.chua.starter.ai.support.model.AiModuleQuery;
import com.chua.starter.ai.support.service.AiSettingFacadeService;
import com.chua.starter.ai.support.service.AiModuleFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * AI 模块门面服务实现。
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiModuleFacadeServiceImpl implements AiModuleFacadeService {

    /** Local_onnx_colorization_module_code */
    public static final String LOCAL_ONNX_COLORIZATION_MODULE_CODE = "local-onnx-colorization";
    /** Local_onnx_resolution_module_code */
    public static final String LOCAL_ONNX_RESOLUTION_MODULE_CODE = "local-onnx-resolution";
    /** Local_onnx_face_enhance_module_code */
    public static final String LOCAL_ONNX_FACE_ENHANCE_MODULE_CODE = "local-onnx-face-enhance";
    /** Local_onnx_face_detect_module_code */
    public static final String LOCAL_ONNX_FACE_DETECT_MODULE_CODE = "local-onnx-face-detect";
    /** Local_onnx_matting_u2net_module_code */
    public static final String LOCAL_ONNX_MATTING_U2NET_MODULE_CODE = "local-onnx-matting-u2net";

    /** AImodulemapper */
    private final AiModuleMapper aiModuleMapper;
    /** AI设置facade服务 */
    private final AiSettingFacadeService aiSettingFacadeService;
    /** SYSAI设置mapper */
    private final SysAiSettingMapper sysAiSettingMapper;

    @Lazy
    @Autowired
    /** Self */
    private AiModuleFacadeServiceImpl self;

    @Override
    public List<SysAiModule> list(String moduleType, String moduleVendor) {
        List<SysAiModule> modules;
        try {
            modules = self.loadAllCached();
        } catch (Exception e) {
            log.warn("查询模块缓存失败，直接查询数据库", e);
            modules = loadAllCached();
        }
        // filter by type
        if (moduleType != null && !moduleType.isBlank()) {
            modules = modules.stream()
                    .filter(m -> moduleType.equals(m.getSysAiModuleType()))
                    .collect(Collectors.toList());
        }
        // filter by vendor
        if (moduleVendor != null && !moduleVendor.isBlank()) {
            modules = modules.stream()
                    .filter(m -> moduleVendor.equals(m.getSysAiModuleVendor()))
                    .collect(Collectors.toList());
        }
        // merge local SPI modules (not cached)
        mergeLocalSpiModules(modules, moduleType, moduleVendor);
        attachCompatFields(modules);
        return modules;
    }

    /**
     * 从数据库加载所有已启用的模块（带缓存）。
     * 模型总量少，全部缓存后内存过滤。
     */
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, key = "'sys:ai:modules:all'", unless = "#result == null")
    public List<SysAiModule> loadAllCached() {
        LambdaQueryWrapper<SysAiModule> wrapper = Wrappers.lambdaQuery(SysAiModule.class)
                .orderByAsc(SysAiModule::getSysAiModuleSort)
                .orderByDesc(SysAiModule::getSysAiModuleId);
        return aiModuleMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> page(AiModuleQuery query) {
        List<SysAiModule> records;
        long total = 0;
        try {
            Page<SysAiModule> page = aiModuleMapper.selectPage(
                    new Page<>(query.getCurrent(), query.getSize()),
                    Wrappers.lambdaQuery(SysAiModule.class)
                            .eq(query.getSysAiModuleType() != null && !query.getSysAiModuleType().isBlank(),
                                    SysAiModule::getSysAiModuleType,
                                    query.getSysAiModuleType())
                            .eq(query.getSysAiModuleVendor() != null && !query.getSysAiModuleVendor().isBlank(),
                                    SysAiModule::getSysAiModuleVendor,
                                    query.getSysAiModuleVendor())
                            .orderByAsc(SysAiModule::getSysAiModuleSort)
                            .orderByDesc(SysAiModule::getSysAiModuleId)
            );
            records = page.getRecords();
            total = page.getTotal();
        } catch (Exception e) {
            log.warn("分页查询模块表失败，仅返回 SPI 本地模块", e);
            records = new ArrayList<>();
        }
        String type = query.getSysAiModuleType();
        String vendor = query.getSysAiModuleVendor();
        mergeLocalSpiModules(records, type, vendor);
        Map<String, Object> result = new LinkedHashMap<>();
        attachCompatFields(records);
        result.put("records", records);
        result.put("total", total);
        result.put("size", query.getSize());
        result.put("current", query.getCurrent());
        result.put("pages", total > 0 ? (total + query.getSize() - 1) / query.getSize() : 0);
        return result;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, key = "'sys:ai:modules:all'")
    public SysAiModule save(SysAiModule entity) {
        entity.setSysAiModuleStatus(entity.getSysAiModuleStatus() == null ? 1 : entity.getSysAiModuleStatus());
        entity.setSysAiModuleSort(entity.getSysAiModuleSort() == null ? 0 : entity.getSysAiModuleSort());
        entity.setSysAiModuleApiExpose(entity.getSysAiModuleApiExpose() == null ? 1 : entity.getSysAiModuleApiExpose());
        entity.setSysAiModuleManufacturers(entity.getSysAiModuleVendor());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        aiModuleMapper.insert(entity);
        return entity;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, key = "'sys:ai:modules:all'")
    public boolean update(SysAiModule entity) {
        entity.setSysAiModuleManufacturers(entity.getSysAiModuleVendor());
        entity.setUpdateTime(LocalDateTime.now());
        return aiModuleMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Integer id) {
        aiSettingFacadeService.deleteByModuleId(id);
        return aiModuleMapper.deleteById(id) > 0;
    }

    @Override
    public SysAiModule getById(Integer id) {
        return aiModuleMapper.selectById(id);
    }

    @Override
    public List<SysAiSetting> settings(Integer moduleId) {
        return sysAiSettingMapper.selectList(
                Wrappers.<SysAiSetting>lambdaQuery()
                        .eq(SysAiSetting::getSysAiSettingModuleId, moduleId)
        );
    }

    private void attachCompatFields(List<SysAiModule> modules) {
    }

    /**
     * 通过 SPI 获取本地模块定义并合并到结果列表。
     */
    private void mergeLocalSpiModules(List<SysAiModule> modules, String moduleType, String moduleVendor) {
        if (modules == null) {
            modules = new ArrayList<>();
        }
        try {
            List<LocalModuleProvider> providers = ServiceProvider.of(LocalModuleProvider.class).collect();
            for (LocalModuleProvider provider : providers) {
                List<LocalModuleDefinition> defs = provider.getModules();
                if (defs == null || defs.isEmpty()) {
                    continue;
                }
                for (LocalModuleDefinition def : defs) {
                    if (!def.isEnabled()) {
                        continue;
                    }
                    // 类型过滤
                    if (moduleType != null && !moduleType.isBlank()
                            && def.getType() != null && !moduleType.equalsIgnoreCase(def.getType())) {
                        continue;
                    }
                    // 去重：已存在同 code 的数据库模块则跳过
                    if (!codeNotExists(modules, def.getCode())) {
                        continue;
                    }
                    SysAiModule module = toSysAiModule(def);
                    module.setSysAiModuleType(def.getType());
                    module.setSysAiModuleDescription(def.getDescription());
                    module.setSysAiModuleStatus(1);
                    module.setSysAiModuleApiExpose(0);
                    modules.add(module);
                }
            }
        } catch (Exception e) {
            log.debug("加载 SPI 本地模块失败（不影响主流程）", e);
        }
    }

    private boolean codeNotExists(List<SysAiModule> modules, String code) {
        return modules.stream().noneMatch(m -> code.equalsIgnoreCase(m.getSysAiModuleCode()));
    }

    private SysAiModule toSysAiModule(LocalModuleDefinition def) {
        SysAiModule module = new SysAiModule();
        module.setSysAiModuleName(def.getName());
        module.setSysAiModuleCode(def.getCode());
        return module;
    }

    private Map<String, Object> buildVincentSetting(Integer moduleId) {
        return Map.of();
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer defaultInt(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }
}
