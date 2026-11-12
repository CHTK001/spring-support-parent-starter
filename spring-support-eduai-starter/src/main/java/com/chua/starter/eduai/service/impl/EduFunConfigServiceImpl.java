package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduFunConfigMapper;
import com.chua.starter.eduai.model.EduFunConfig;
import com.chua.starter.eduai.service.IEduFunConfigService;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 趣味模块配置服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class EduFunConfigServiceImpl extends ServiceImpl<EduFunConfigMapper, EduFunConfig>
        implements IEduFunConfigService {

    /**
     * 允许的学段值：留空表示通用（管理端）模块。
     */
    private static final Set<String> GRADE_LEVELS = Set.of("PRIMARY", "JUNIOR", "SENIOR", "TEACHER");

    /**
     * 模块编码：字母/数字开头，只含字母、数字、下划线、短横线，最长 64 位。
     */
    private static final Pattern MODULE_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    @Override
    public List<EduFunConfig> listEnabled() {
        return getBaseMapper().selectEnabledList(EduUserContext.ownerScope());
    }

    @Override
    public List<EduFunConfig> listAllScoped() {
        return getBaseMapper().selectScopedList(EduUserContext.ownerScope());
    }

    @Override
    public EduFunConfig getByModuleCode(String moduleCode, String gradeLevel) {
        return getBaseMapper().selectByModuleCode(moduleCode, normalizeLevel(gradeLevel), EduUserContext.ownerScope());
    }

    @Override
    public boolean updateEnabled(String moduleCode, String gradeLevel, Integer enabled) {
        EduFunConfig config = getByModuleCode(moduleCode, gradeLevel);
        if (config == null) {
            return false;
        }
        EduUserContext.checkWritable(config.getOwnerUserId());
        config.setEnabled(enabled);
        return updateById(config);
    }

    @Override
    public List<EduFunConfig> listPortalMenu(String gradeLevel) {
        String level = normalizeLevel(gradeLevel);
        if (level == null) {
            return List.of();
        }
        String owner = EduUserContext.ownerScope().ownerPredicate();
        LambdaQueryWrapper<EduFunConfig> query = new LambdaQueryWrapper<>();
        // 只取门户需要的列：不把 configJson、归属人等信息带给学生/老师
        query.select(EduFunConfig::getModuleCode,
                        EduFunConfig::getModuleName,
                        EduFunConfig::getModulePath,
                        EduFunConfig::getFunConfigGradeLevel,
                        EduFunConfig::getEnabled,
                        EduFunConfig::getSortOrder)
                .eq(EduFunConfig::getFunConfigGradeLevel, level);
        if (owner != null) {
            query.and(w -> w.isNull(EduFunConfig::getOwnerUserId).or().eq(EduFunConfig::getOwnerUserId, owner));
        }
        query.orderByAsc(EduFunConfig::getSortOrder);
        return list(query);
    }

    /**
     * 学段归一化：去空白并转大写，空串归一成 null。
     *
     * @param gradeLevel 原值
     * @return 归一化后的学段或 null
     */
    private static String normalizeLevel(String gradeLevel) {
        String trimmed = trimToNull(gradeLevel);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    @Override
    public List<EduFunConfig> listByLevel(String gradeLevel) {
        if (gradeLevel == null || gradeLevel.isBlank()) {
            return List.of();
        }
        return getBaseMapper().selectByLevel(gradeLevel.trim().toUpperCase(java.util.Locale.ROOT),
                EduUserContext.ownerScope());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduFunConfig saveScoped(EduFunConfig config) {
        prepare(config, new HashSet<>());
        saveOrUpdate(config);
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EduFunConfig> saveBatchScoped(List<EduFunConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        Set<String> keys = new HashSet<>();
        List<EduFunConfig> saved = new ArrayList<>(configs.size());
        for (EduFunConfig config : configs) {
            prepare(config, keys);
            saveOrUpdate(config);
            saved.add(config);
        }
        return saved;
    }

    /**
     * 保存前的统一校验与归属处理。
     *
     * <p>学段写成小写或拼错时，门户按大写查会永远查不到，入口静默消失；
     * 模块编码带空格/为空时同样会写出一个点不开的卡片。这里把两类都拦在入库前，
     * 并对同批次与库里已有的（编码 + 学段）查重，避免把驱动异常抛给前端。</p>
     *
     * @param config    配置
     * @param batchKeys 本批次已占用的（学段|编码），单条保存时传空集合
     */
    private void prepare(EduFunConfig config, Set<String> batchKeys) {
        String level = trimToNull(config.getFunConfigGradeLevel());
        if (level == null) {
            config.setFunConfigGradeLevel(null);
        } else {
            String normalized = level.toUpperCase(Locale.ROOT);
            if (!GRADE_LEVELS.contains(normalized)) {
                throw new BusinessException("适用学段只能是 PRIMARY / JUNIOR / SENIOR / TEACHER，留空表示通用");
            }
            config.setFunConfigGradeLevel(normalized);
        }

        String code = trimToNull(config.getModuleCode());
        if (code == null) {
            throw new BusinessException("模块编码不能为空");
        }
        if (!MODULE_CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException("模块编码只能包含字母、数字、下划线和短横线，且不超过 64 位");
        }
        config.setModuleCode(code);

        String name = trimToNull(config.getModuleName());
        if (name == null) {
            throw new BusinessException("模块名称不能为空");
        }
        config.setModuleName(name);
        config.setFunConfigSubtitle(trimToNull(config.getFunConfigSubtitle()));
        config.setModuleDesc(trimToNull(config.getModuleDesc()));
        config.setModuleIcon(trimToNull(config.getModuleIcon()));
        config.setModulePath(trimToNull(config.getModulePath()));

        String batchKey = (config.getFunConfigGradeLevel() == null ? "" : config.getFunConfigGradeLevel())
                + "|" + code.toLowerCase(Locale.ROOT);
        if (!batchKeys.add(batchKey)) {
            throw new BusinessException("模块编码「" + code + "」在本批次内重复");
        }

        LambdaQueryWrapper<EduFunConfig> query = new LambdaQueryWrapper<>();
        query.eq(EduFunConfig::getModuleCode, code);
        if (config.getFunConfigGradeLevel() == null) {
            query.isNull(EduFunConfig::getFunConfigGradeLevel);
        } else {
            query.eq(EduFunConfig::getFunConfigGradeLevel, config.getFunConfigGradeLevel());
        }
        if (config.getConfigId() != null) {
            query.ne(EduFunConfig::getConfigId, config.getConfigId());
        }
        if (count(query) > 0) {
            throw new BusinessException("模块编码「" + code + "」在当前范围已存在");
        }

        if (config.getConfigId() == null) {
            // 门户菜单是全局配置：新建行不绑定创建人，否则门户按 owner 过滤后看不到
            EduUserContext.requireUserId();
            config.setOwnerUserId(null);
            return;
        }
        EduFunConfig exists = getById(config.getConfigId());
        if (exists == null) {
            throw new BusinessException("趣味模块配置不存在");
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        config.setOwnerUserId(exists.getOwnerUserId());
    }

    /**
     * 去首尾空白，空串归一成 null。
     *
     * @param value 原值
     * @return 去空白后的值或 null
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
