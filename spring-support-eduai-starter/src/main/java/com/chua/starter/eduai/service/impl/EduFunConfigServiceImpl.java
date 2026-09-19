package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        LambdaQueryWrapper<EduFunConfig> wrapper = new LambdaQueryWrapper<EduFunConfig>()
                .eq(EduFunConfig::getEnabled, 1)
                .orderByAsc(EduFunConfig::getSortOrder);
        EduUserContext.scopeOwned(wrapper, EduFunConfig::getOwnerUserId);
        return list(wrapper);
    }

    @Override
    public List<EduFunConfig> listAllScoped() {
        LambdaQueryWrapper<EduFunConfig> wrapper = new LambdaQueryWrapper<EduFunConfig>()
                .orderByAsc(EduFunConfig::getSortOrder);
        EduUserContext.scopeOwned(wrapper, EduFunConfig::getOwnerUserId);
        return list(wrapper);
    }

    @Override
    public EduFunConfig getByModuleCode(String moduleCode) {
        LambdaQueryWrapper<EduFunConfig> wrapper = new LambdaQueryWrapper<EduFunConfig>()
                .eq(EduFunConfig::getModuleCode, moduleCode);
        EduUserContext.scopeOwned(wrapper, EduFunConfig::getOwnerUserId);
        return getOne(wrapper, false);
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
}
