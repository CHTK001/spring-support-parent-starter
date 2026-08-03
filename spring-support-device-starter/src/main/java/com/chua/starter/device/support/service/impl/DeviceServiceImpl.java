package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceOrganization;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.mapper.DeviceOrganizationMapper;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 设备服务实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    /**
     * 设备信息 Mapper
     */
    private final DeviceInfoMapper deviceInfoMapper;

    /**
     * 设备组织 Mapper
     */
    private final DeviceOrganizationMapper deviceOrganizationMapper;

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;

    /**
     * 所有可用的设备 Provider（由 SPI 自动注入）
     */
    private final List<DeviceProvider> providers;

    @Override
    public DeviceInfo addDevice(DeviceConnectInfo info) {
        DeviceProvider provider = providers.stream()
                .filter(p -> p.support(info))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("不支持的设备厂商"));

        DeviceSession session = provider.connect(info);

        DeviceInfo device = new DeviceInfo();
        device.setDeviceInfoId(UUID.randomUUID().toString());
        device.setDeviceInfoVendor(info.getVendor());
        device.setDeviceInfoIp(info.getIp());
        device.setDeviceInfoPort(info.getPort());
        device.setDeviceInfoUsername(info.getUsername());
        device.setDeviceInfoPassword(info.getPassword());
        device.setDeviceInfoOnline(Boolean.TRUE);
        device.setDeviceInfoCreatedAt(LocalDateTime.now());
        deviceInfoMapper.insert(device);

        sessionManager.register(device.getDeviceInfoId(), session);
        log.info("设备添加成功: vendor={}, ip={}", info.getVendor(), info.getIp());
        return device;
    }

    @Override
    public void updateDevice(String id, DeviceInfo info) {
        info.setDeviceInfoId(id);
        deviceInfoMapper.updateById(info);
    }

    @Override
    public void deleteDevice(String id) {
        deviceInfoMapper.deleteById(id);
        sessionManager.unregister(id);
    }

    @Override
    public DeviceInfo getDevice(String id) {
        return deviceInfoMapper.selectById(id);
    }

    @Override
    public List<DeviceInfo> listDevices(String orgId) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(orgId)) {
            wrapper.eq(DeviceInfo::getDeviceInfoOrgId, orgId);
        }
        wrapper.orderByDesc(DeviceInfo::getDeviceInfoCreatedAt);
        return deviceInfoMapper.selectList(wrapper);
    }

    @Override
    public List<DeviceInfo> listByCategory(String category) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceInfo::getDeviceInfoCategory, category);
        return deviceInfoMapper.selectList(wrapper);
    }

    @Override
    public void syncOrganization() {
        for (DeviceProvider provider : providers) {
            try {
                List<com.chua.starter.device.spi.model.OrganizationNode> nodes = provider.fetchOrganizations();
                if (nodes == null || nodes.isEmpty()) {
                    log.info("组织同步: vendor={} 无组织节点", provider.getVendor());
                    continue;
                }
                for (com.chua.starter.device.spi.model.OrganizationNode node : nodes) {
                    DeviceOrganization org = new DeviceOrganization();
                    org.setDeviceOrganizationId(provider.getVendor() + ":" + node.getOrgId());
                    org.setDeviceOrganizationName(node.getOrgName());
                    org.setDeviceOrganizationVendor(provider.getVendor());
                    org.setDeviceOrganizationParentId(
                            node.getParentId() == null ? null : provider.getVendor() + ":" + node.getParentId());
                    deviceOrganizationMapper.insert(org);
                }
                log.info("组织同步: vendor={}, count={}", provider.getVendor(), nodes.size());
            } catch (Exception e) {
                log.error("组织同步失败: vendor={}", provider.getVendor(), e);
            }
        }
    }

    @Override
    public List<DeviceOrganization> getOrganizationTree(String vendor) {
        LambdaQueryWrapper<DeviceOrganization> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(vendor)) {
            wrapper.eq(DeviceOrganization::getDeviceOrganizationVendor, vendor);
        }
        return deviceOrganizationMapper.selectList(wrapper);
    }

    @Override
    public void updateOfflineThreshold(String id, int threshold) {
        DeviceInfo device = deviceInfoMapper.selectById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: " + id);
        }
        DeviceInfo update = new DeviceInfo();
        update.setDeviceInfoId(id);
        update.setDeviceInfoOfflineThreshold(threshold);
        update.setDeviceInfoUpdatedAt(LocalDateTime.now());
        deviceInfoMapper.updateById(update);
        log.info("设备离线检测阈值更新: id={}, threshold={}秒", id, threshold);
    }

    @Override
    public void reconnect(String id) {
        DeviceInfo device = deviceInfoMapper.selectById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: " + id);
        }
        // 关闭旧会话
        sessionManager.unregister(id);
        // 按设备现有信息重建连接
        DeviceConnectInfo info = new DeviceConnectInfo();
        info.setVendor(device.getDeviceInfoVendor());
        info.setIp(device.getDeviceInfoIp());
        info.setPort(device.getDeviceInfoPort());
        info.setUsername(device.getDeviceInfoUsername());
        info.setPassword(device.getDeviceInfoPassword());
        DeviceSession session = providers.stream()
                .filter(p -> p.support(info))
                .findFirst()
                .map(p -> p.connect(info))
                .orElse(null);
        if (session == null) {
            throw new RuntimeException("设备重连失败，无可用 Provider: " + id);
        }
        sessionManager.register(id, session);
        DeviceInfo update = new DeviceInfo();
        update.setDeviceInfoId(id);
        update.setDeviceInfoOnline(Boolean.TRUE);
        update.setDeviceInfoUpdatedAt(LocalDateTime.now());
        deviceInfoMapper.updateById(update);
        log.info("设备重连成功: id={}, ip={}", id, device.getDeviceInfoIp());
    }

    @Override
    public void refreshStatus(String id) {
        DeviceInfo device = deviceInfoMapper.selectById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: " + id);
        }
        DeviceSession session = sessionManager.getSession(id);
        boolean online = session != null && session.isOnline() && session.testConnection();
        DeviceInfo update = new DeviceInfo();
        update.setDeviceInfoId(id);
        update.setDeviceInfoOnline(online);
        update.setDeviceInfoUpdatedAt(LocalDateTime.now());
        deviceInfoMapper.updateById(update);
        log.debug("设备状态刷新: id={}, online={}", id, online);
    }

    @Override
    public boolean testConnection(DeviceConnectInfo info) {
        DeviceSession session = providers.stream()
                .filter(p -> p.support(info))
                .findFirst()
                .map(p -> {
                    try {
                        return p.connect(info);
                    } catch (Exception e) {
                        log.warn("设备连接失败: {}", e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
        if (session == null) {
            return false;
        }
        try {
            return session.testConnection();
        } finally {
            session.close();
        }
    }
}