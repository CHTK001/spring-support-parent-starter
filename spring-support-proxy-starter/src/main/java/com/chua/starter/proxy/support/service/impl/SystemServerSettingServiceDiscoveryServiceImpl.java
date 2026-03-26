package com.chua.starter.proxy.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingServiceDiscovery;
import com.chua.starter.proxy.support.entity.SystemServerSettingServiceDiscoveryMapping;
import com.chua.starter.proxy.support.mapper.SystemServerSettingServiceDiscoveryMapper;
import com.chua.starter.proxy.support.mapper.SystemServerSettingServiceDiscoveryMappingMapper;
import com.chua.starter.proxy.support.service.server.SystemServerSettingService;
import com.chua.starter.proxy.support.service.server.SystemServerSettingServiceDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemServerSettingServiceDiscoveryServiceImpl extends ServiceImpl<SystemServerSettingServiceDiscoveryMapper, SystemServerSettingServiceDiscovery>
        implements SystemServerSettingServiceDiscoveryService {

    private final SystemServerSettingService systemServerSettingService;
    private final SystemServerSettingServiceDiscoveryMappingMapper mappingMapper;

    @Override
    public ReturnResult<SystemServerSettingServiceDiscovery> saveOrUpdateConfig(SystemServerSettingServiceDiscovery config) {
        try {
            if (config.getSystemServerSettingServiceDiscoveryId() == null) {
                save(config);
            } else {
                updateById(config);
            }
            // 保存后热应用
            systemServerSettingService.applyConfigToRunningServer(config.getServiceDiscoveryServerId());
            return ReturnResult.ok(config);
        } catch (Exception e) {
            log.error("保存ServiceDiscovery配置失败", e);
            return ReturnResult.error("保存失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> removeByServerId(Integer serverId) {
        try {
            lambdaUpdate().eq(SystemServerSettingServiceDiscovery::getServiceDiscoveryServerId, serverId).remove();
            // 同时删除该服务器下的映射
            mappingMapper.delete(new QueryWrapper<SystemServerSettingServiceDiscoveryMapping>()
                    .eq("service_discovery_server_id", serverId));
            // 删除后热应用
            systemServerSettingService.applyConfigToRunningServer(serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("删除ServiceDiscovery配置失败", e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    public List<SystemServerSettingServiceDiscovery> listByServerId(Integer serverId) {
        return lambdaQuery().eq(SystemServerSettingServiceDiscovery::getServiceDiscoveryServerId, serverId).list();
    }

    @Override
    public List<SystemServerSettingServiceDiscoveryMapping> listMappingsByServerId(Integer serverId) {
        return mappingMapper.selectList(new QueryWrapper<SystemServerSettingServiceDiscoveryMapping>()
                .eq("service_discovery_server_id", serverId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> saveOrUpdateMappings(Integer serverId, List<SystemServerSettingServiceDiscoveryMapping> mappings) {
        try {
            // 先清空该服务器下的映射，再批量插入
            mappingMapper.delete(new QueryWrapper<SystemServerSettingServiceDiscoveryMapping>()
                    .eq("service_discovery_server_id", serverId));
            if (mappings != null) {
                for (SystemServerSettingServiceDiscoveryMapping m : mappings) {
                    m.setServiceDiscoveryServerId(serverId);
                    mappingMapper.insert(m);
                }
            }
            // 保存后热应用
            systemServerSettingService.applyConfigToRunningServer(serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("保存ServiceDiscovery映射失败", e);
            return ReturnResult.error("保存失败: " + e.getMessage());
        }
    }
}





