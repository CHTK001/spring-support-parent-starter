package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.eduai.mapper.EduFunConfigMapper;
import com.chua.starter.eduai.model.EduFunConfig;
import com.chua.starter.eduai.service.IEduFunConfigService;
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
        return list(new LambdaQueryWrapper<EduFunConfig>()
                .eq(EduFunConfig::getEnabled, 1)
                .orderByAsc(EduFunConfig::getSortOrder));
    }

    @Override
    public EduFunConfig getByModuleCode(String moduleCode) {
        return getOne(new LambdaQueryWrapper<EduFunConfig>()
                .eq(EduFunConfig::getModuleCode, moduleCode));
    }

    @Override
    public boolean updateEnabled(String moduleCode, Integer enabled) {
        EduFunConfig config = getByModuleCode(moduleCode);
        if (config == null) {
            return false;
        }
        config.setEnabled(enabled);
        return updateById(config);
    }
}
