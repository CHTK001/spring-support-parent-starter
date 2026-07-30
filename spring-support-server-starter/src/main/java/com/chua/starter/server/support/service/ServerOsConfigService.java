package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.OsConfigApplyResult;
import com.chua.starter.server.support.model.ServerOsConfigItem;
import java.util.List;

public interface ServerOsConfigService {

    /**
     * 获取指定服务器的所有 OS 配置项清单。
     */
    List<ServerOsConfigItem> listConfigs(Integer serverId) throws Exception;

    /**
     * 获取指定配置项的当前值。
     */
    ServerOsConfigItem getConfig(Integer serverId, String key) throws Exception;

    /**
     * 应用配置变更。
     */
    OsConfigApplyResult applyConfig(Integer serverId, String key, String value) throws Exception;
}
