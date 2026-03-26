package com.chua.starter.proxy.support.service.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingFileStorage;

import java.util.List;

public interface SystemServerSettingFileStorageService extends IService<SystemServerSettingFileStorage> {

    List<SystemServerSettingFileStorage> listByServerId(Integer serverId);

    ReturnResult<Boolean> replaceAllForServer(Integer serverId, List<SystemServerSettingFileStorage> configs);

    ReturnResult<Boolean> saveOne(SystemServerSettingFileStorage config);
}






