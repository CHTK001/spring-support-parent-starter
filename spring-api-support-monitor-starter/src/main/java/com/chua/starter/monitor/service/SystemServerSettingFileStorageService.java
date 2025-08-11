package com.chua.starter.monitor.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.entity.SystemServerSettingFileStorage;

import java.util.List;

public interface SystemServerSettingFileStorageService {

    List<SystemServerSettingFileStorage> listByServerId(Integer serverId);

    ReturnResult<SystemServerSettingFileStorage> saveOrUpdate(SystemServerSettingFileStorage config);

    ReturnResult<Boolean> deleteByServerId(Integer serverId);
}

