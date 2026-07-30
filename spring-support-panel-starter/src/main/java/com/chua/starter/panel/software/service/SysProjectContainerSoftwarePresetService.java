package com.chua.starter.panel.software.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.panel.software.entity.SysProjectContainerSoftwarePreset;

import java.util.Set;

public interface SysProjectContainerSoftwarePresetService extends IService<SysProjectContainerSoftwarePreset> {

    IPage<SysProjectContainerSoftwarePreset> pageFor(Query<SysProjectContainerSoftwarePreset> query,
                                                     SysProjectContainerSoftwarePreset request,
                                                     Set<String> roles);

    ReturnResult<Boolean> delete(Integer presetId);

    ReturnResult<Boolean> update(Set<String> roles, SysProjectContainerSoftwarePreset preset);

    ReturnResult<SysProjectContainerSoftwarePreset> save(SysProjectContainerSoftwarePreset preset, Set<String> roles);
}
