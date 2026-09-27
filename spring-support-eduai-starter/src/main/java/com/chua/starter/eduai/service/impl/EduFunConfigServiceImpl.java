package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.eduai.mapper.EduFunConfigMapper;
import com.chua.starter.eduai.model.EduFunConfig;
import com.chua.starter.eduai.service.IEduFunConfigService;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 趣味模块配置服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class EduFunConfigServiceImpl extends ServiceImpl<EduFunConfigMapper, EduFunConfig>
        implements IEduFunConfigService {

    @Override
    public List<EduFunConfig> listEnabled() {
        return getBaseMapper().selectEnabledList(EduUserContext.ownerScope());
    }

    @Override
    public List<EduFunConfig> listAllScoped() {
        return getBaseMapper().selectScopedList(EduUserContext.ownerScope());
    }

    @Override
    public EduFunConfig getByModuleCode(String moduleCode) {
        return getBaseMapper().selectByModuleCode(moduleCode, EduUserContext.ownerScope());
    }

    @Override
    public boolean updateEnabled(String moduleCode, Integer enabled) {
        EduFunConfig config = getByModuleCode(moduleCode);
        if (config == null) {
            return false;
        }
        EduUserContext.checkWritable(config.getOwnerUserId());
        config.setEnabled(enabled);
        return updateById(config);
    }

    @Override
    public List<EduFunConfig> listByLevel(String gradeLevel) {
        if (gradeLevel == null || gradeLevel.isBlank()) {
            return List.of();
        }
        return getBaseMapper().selectByLevel(gradeLevel.trim().toUpperCase(java.util.Locale.ROOT),
                EduUserContext.ownerScope());
    }
}
