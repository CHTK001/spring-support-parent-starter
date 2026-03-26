package com.chua.starter.proxy.support.service.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingAddressRateLimit;
import com.chua.starter.proxy.support.entity.SystemServerSettingIPRateLimit;

import java.util.List;

/**
 * 限流规则服务（IP/地址）
 */
public interface SystemServerSettingRateLimitService {

    // IP（按 serverId + settingId 精确区分）
    List<SystemServerSettingIPRateLimit> listIpBySetting(Integer serverId, Integer settingId);

    ReturnResult<Boolean> saveIpRules(Integer serverId, Integer settingId, List<SystemServerSettingIPRateLimit> rules);

    ReturnResult<Boolean> deleteIpRules(Integer serverId, Integer settingId);

    // 地址（按 serverId + settingId 精确区分）
    List<SystemServerSettingAddressRateLimit> listAddressBySetting(Integer serverId, Integer settingId);

    ReturnResult<Boolean> saveAddressRules(Integer serverId, Integer settingId, List<SystemServerSettingAddressRateLimit> rules);

    ReturnResult<Boolean> deleteAddressRules(Integer serverId, Integer settingId);
}






