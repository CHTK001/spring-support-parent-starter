package com.chua.starter.monitor.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.entity.SystemServerSettingIPRateLimit;
import com.chua.starter.monitor.entity.SystemServerSettingAddressRateLimit;

import java.util.List;

/**
 * 限流规则服务：封装IP与地址限流的CRUD与热应用
 */
public interface SystemServerSettingRateLimitService {

    // IP限流
    List<SystemServerSettingIPRateLimit> listIpRules(Integer serverId);

    ReturnResult<Boolean> saveIpRules(Integer serverId, List<SystemServerSettingIPRateLimit> rules);

    ReturnResult<Boolean> deleteIpRulesByServer(Integer serverId);

    // 地址限流
    List<SystemServerSettingAddressRateLimit> listAddressRules(Integer serverId);

    ReturnResult<Boolean> saveAddressRules(Integer serverId, List<SystemServerSettingAddressRateLimit> rules);

    ReturnResult<Boolean> deleteAddressRulesByServer(Integer serverId);
}

