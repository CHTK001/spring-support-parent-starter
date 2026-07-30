package com.chua.starter.panel.software.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.panel.software.entity.SysProjectContainerSoftwarePreset;
import com.chua.starter.panel.software.mapper.SysProjectContainerSoftwarePresetMapper;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwarePresetService;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SysProjectContainerSoftwarePresetServiceImpl
        extends ServiceImpl<SysProjectContainerSoftwarePresetMapper, SysProjectContainerSoftwarePreset>
        implements SysProjectContainerSoftwarePresetService {

    @Override
    public IPage<SysProjectContainerSoftwarePreset> pageFor(Query<SysProjectContainerSoftwarePreset> query,
                                                            SysProjectContainerSoftwarePreset request,
                                                            Set<String> roles) {
        return baseMapper.selectPage(query.createPage(), query.mpjLambda()
                .selectAll(SysProjectContainerSoftwarePreset.class)
                .like(StringUtils.isNotBlank(request.getSoftwareName()),
                        SysProjectContainerSoftwarePreset::getSoftwareName, request.getSoftwareName())
                .like(StringUtils.isNotBlank(request.getSoftwareCode()),
                        SysProjectContainerSoftwarePreset::getSoftwareCode, request.getSoftwareCode())
                .eq(StringUtils.isNotBlank(request.getSoftwareCategory()),
                        SysProjectContainerSoftwarePreset::getSoftwareCategory, request.getSoftwareCategory())
                .eq(StringUtils.isNotBlank(request.getOsType()),
                        SysProjectContainerSoftwarePreset::getOsType, request.getOsType())
                .eq(StringUtils.isNotBlank(request.getSoftwareArch()),
                        SysProjectContainerSoftwarePreset::getSoftwareArch, request.getSoftwareArch())
                .eq(null != request.getPresetStatus(),
                        SysProjectContainerSoftwarePreset::getPresetStatus, request.getPresetStatus())
                .eq(null != request.getPresetDelete(),
                        SysProjectContainerSoftwarePreset::getPresetDelete, request.getPresetDelete())
                .orderByAsc(SysProjectContainerSoftwarePreset::getPresetSort)
                .orderByAsc(SysProjectContainerSoftwarePreset::getSysProjectContainerSoftwarePresetId));
    }

    @Override
    public ReturnResult<Boolean> delete(Integer presetId) {
        return ReturnResult.guess(baseMapper.deleteById(presetId) > 0);
    }

    @Override
    public ReturnResult<Boolean> update(Set<String> roles, SysProjectContainerSoftwarePreset preset) {
        return ReturnResult.guess(baseMapper.updateById(preset) > 0);
    }

    @Override
    public ReturnResult<SysProjectContainerSoftwarePreset> save(SysProjectContainerSoftwarePreset preset, Set<String> roles) {
        if ("DOCKERFILE".equals(preset.getPresetType()) && StringUtils.isBlank(preset.getInstallScript())) {
            return ReturnResult.illegal("DOCKERFILE 类型必须提供 Dockerfile 内容");
        }
        if ("ENVIRONMENT".equals(preset.getPresetType()) && StringUtils.isBlank(preset.getStatusScript())) {
            return ReturnResult.illegal("ENVIRONMENT 类型必须提供状态检测脚本");
        }
        preset.setPresetDelete(0);
        if (null == preset.getPresetStatus()) {
            preset.setPresetStatus(1);
        }
        if (null == preset.getPresetBuiltin()) {
            preset.setPresetBuiltin(0);
        }
        if (null == preset.getPresetSort()) {
            preset.setPresetSort(0);
        }
        baseMapper.insert(preset);
        return ReturnResult.guess(preset);
    }
}
