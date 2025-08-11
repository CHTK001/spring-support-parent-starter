package com.chua.starter.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.entity.SystemServerSettingAddressRateLimit;
import com.chua.starter.monitor.entity.SystemServerSettingIPRateLimit;
import com.chua.starter.monitor.mapper.SystemServerSettingAddressRateLimitMapper;
import com.chua.starter.monitor.mapper.SystemServerSettingIPRateLimitMapper;
import com.chua.starter.monitor.service.SystemServerSettingRateLimitService;
import com.chua.starter.monitor.service.SystemServerSettingService;
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
    private final SystemServerSettingAddressRateLimitMapper addressMapper;
    private final SystemServerSettingService systemServerSettingService;

    @Override
    public List<SystemServerSettingIPRateLimit> listIpRules(Integer serverId) {
        return ipMapper.selectByServerId(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> saveIpRules(Integer serverId, List<SystemServerSettingIPRateLimit> rules) {
        try {
            ipMapper.delete(new QueryWrapper<SystemServerSettingIPRateLimit>().eq("ip_rate_limit_server_id", serverId));
            if (rules != null) {
                for (SystemServerSettingIPRateLimit rule : rules) {
                    rule.setIpRateLimitServerId(serverId);
                    ipMapper.insert(rule);
                }
            }
            systemServerSettingService.applyConfigToRunningServer(serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("保存IP限流规则失败", e);
            return ReturnResult.error("保存失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> deleteIpRulesByServer(Integer serverId) {
        try {
            ipMapper.delete(new QueryWrapper<SystemServerSettingIPRateLimit>().eq("ip_rate_limit_server_id", serverId));
            systemServerSettingService.applyConfigToRunningServer(serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("删除IP限流规则失败", e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    public List<SystemServerSettingAddressRateLimit> listAddressRules(Integer serverId) {
        return addressMapper.selectByServerId(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> saveAddressRules(Integer serverId, List<SystemServerSettingAddressRateLimit> rules) {
        try {
            addressMapper.delete(new QueryWrapper<SystemServerSettingAddressRateLimit>().eq("address_rate_limit_server_id", serverId));
            if (rules != null) {
                for (SystemServerSettingAddressRateLimit rule : rules) {
                    rule.setAddressRateLimitServerId(serverId);
                    addressMapper.insert(rule);
                }
            }
            systemServerSettingService.applyConfigToRunningServer(serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("保存地址限流规则失败", e);
            return ReturnResult.error("保存失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> deleteAddressRulesByServer(Integer serverId) {
        try {
            addressMapper.delete(new QueryWrapper<SystemServerSettingAddressRateLimit>().eq("address_rate_limit_server_id", serverId));
            systemServerSettingService.applyConfigToRunningServer(serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("删除地址限流规则失败", e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }
}

