package com.chua.starter.proxy.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingAddressRateLimit;
import com.chua.starter.proxy.support.entity.SystemServerSettingIPRateLimit;
import com.chua.starter.proxy.support.mapper.SystemServerSettingAddressRateLimitMapper;
import com.chua.starter.proxy.support.mapper.SystemServerSettingIPRateLimitMapper;
import com.chua.starter.proxy.support.service.server.SystemServerSettingRateLimitService;
import com.chua.starter.proxy.support.service.server.SystemServerSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemServerSettingRateLimitServiceImpl implements SystemServerSettingRateLimitService {

    private final SystemServerSettingIPRateLimitMapper ipMapper;
    private final SystemServerSettingAddressRateLimitMapper addrMapper;
    private final SystemServerSettingService systemServerSettingService;

    @Override
    public List<SystemServerSettingIPRateLimit> listIpBySetting(Integer serverId, Integer settingId) {
        LambdaQueryWrapper<SystemServerSettingIPRateLimit> qw = Wrappers.lambdaQuery(SystemServerSettingIPRateLimit.class)
                .eq(SystemServerSettingIPRateLimit::getIpRateLimitServerId, serverId)
                .eq(SystemServerSettingIPRateLimit::getIpRateLimitSettingId, settingId)
                .orderByAsc(SystemServerSettingIPRateLimit::getSystemServerSettingIpRateLimitId);
        return ipMapper.selectList(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> saveIpRules(Integer serverId, Integer settingId, List<SystemServerSettingIPRateLimit> rules) {
        // 先删除该 settingId 下的规则，再插入
        deleteIpRules(serverId, settingId);
        if (rules != null) {
            for (SystemServerSettingIPRateLimit r : rules) {
                r.setSystemServerSettingIpRateLimitId(null);
                r.setIpRateLimitServerId(serverId);
                r.setIpRateLimitSettingId(settingId);
                ipMapper.insert(r);
            }
        }
        systemServerSettingService.applyConfigToRunningServer(serverId);
        return ReturnResult.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> deleteIpRules(Integer serverId, Integer settingId) {
        LambdaQueryWrapper<SystemServerSettingIPRateLimit> qw = Wrappers.lambdaQuery(SystemServerSettingIPRateLimit.class)
                .eq(SystemServerSettingIPRateLimit::getIpRateLimitServerId, serverId)
                .eq(SystemServerSettingIPRateLimit::getIpRateLimitSettingId, settingId);
        ipMapper.delete(qw);
        systemServerSettingService.applyConfigToRunningServer(serverId);
        return ReturnResult.ok(true);
    }

    @Override
    public List<SystemServerSettingAddressRateLimit> listAddressBySetting(Integer serverId, Integer settingId) {
        LambdaQueryWrapper<SystemServerSettingAddressRateLimit> qw = Wrappers.lambdaQuery(SystemServerSettingAddressRateLimit.class)
                .eq(SystemServerSettingAddressRateLimit::getAddressRateLimitServerId, serverId)
                .eq(SystemServerSettingAddressRateLimit::getAddressRateLimitSettingId, settingId)
                .orderByAsc(SystemServerSettingAddressRateLimit::getSystemServerSettingAddressRateLimitId);
        return addrMapper.selectList(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> saveAddressRules(Integer serverId, Integer settingId, List<SystemServerSettingAddressRateLimit> rules) {
        deleteAddressRules(serverId, settingId);
        if (rules != null) {
            for (SystemServerSettingAddressRateLimit r : rules) {
                r.setSystemServerSettingAddressRateLimitId(null);
                r.setAddressRateLimitServerId(serverId);
                r.setAddressRateLimitSettingId(settingId);
                addrMapper.insert(r);
            }
        }
        systemServerSettingService.applyConfigToRunningServer(serverId);
        return ReturnResult.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> deleteAddressRules(Integer serverId, Integer settingId) {
        LambdaQueryWrapper<SystemServerSettingAddressRateLimit> qw = Wrappers.lambdaQuery(SystemServerSettingAddressRateLimit.class)
                .eq(SystemServerSettingAddressRateLimit::getAddressRateLimitServerId, serverId)
                .eq(SystemServerSettingAddressRateLimit::getAddressRateLimitSettingId, settingId);
        addrMapper.delete(qw);
        systemServerSettingService.applyConfigToRunningServer(serverId);
        return ReturnResult.ok(true);
    }
}






