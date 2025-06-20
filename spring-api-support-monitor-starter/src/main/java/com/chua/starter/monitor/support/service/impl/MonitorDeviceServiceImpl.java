package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.support.entity.MonitorDeviceInfo;
import com.chua.starter.monitor.support.entity.MonitorDeviceMetrics;
import com.chua.starter.monitor.support.mapper.MonitorDeviceInfoMapper;
import com.chua.starter.monitor.support.mapper.MonitorDeviceMetricsMapper;
import com.chua.starter.monitor.support.service.MonitorDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备监控服务实现
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorDeviceServiceImpl implements MonitorDeviceService {

    private final MonitorDeviceMetricsMapper metricsMapper;
    private final MonitorDeviceInfoMapper deviceInfoMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveDeviceMetrics(MonitorDeviceMetrics metrics) {
        try {
            if (metrics == null || StringUtils.isBlank(metrics.getMonitorDeviceMetricsDeviceId())) {
                log.warn("设备指标数据为空或设备ID为空，跳过保存");
                return false;
            }

            // 设置创建和更新时间
            LocalDateTime now = LocalDateTime.now();
            metrics.setMonitorDeviceMetricsCreateTime(now);
            metrics.setMonitorDeviceMetricsUpdateTime(now);

            // 如果没有收集时间，使用当前时间
            if (metrics.getMonitorDeviceMetricsCollectTime() == null) {
                metrics.setMonitorDeviceMetricsCollectTime(now);
            }

            int result = metricsMapper.insert(metrics);
            
            // 更新设备最后上报时间
            updateDeviceLastReportTime(metrics.getMonitorDeviceMetricsDeviceId(), now);
            
            log.debug("保存设备指标数据成功: {}", metrics.getMonitorDeviceMetricsDeviceId());
            return result > 0;
            
        } catch (Exception e) {
            log.error("保存设备指标数据失败", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateDeviceInfo(MonitorDeviceInfo deviceInfo) {
        try {
            if (deviceInfo == null || StringUtils.isBlank(deviceInfo.getMonitorDeviceInfoDeviceId())) {
                log.warn("设备信息为空或设备ID为空，跳过保存");
                return false;
            }

            // 查询是否已存在
            MonitorDeviceInfo existing = deviceInfoMapper.selectByDeviceId(
                deviceInfo.getMonitorDeviceInfoDeviceId());

            LocalDateTime now = LocalDateTime.now();
            
            if (existing == null) {
                // 新增设备
                deviceInfo.setMonitorDeviceInfoCreateTime(now);
                deviceInfo.setMonitorDeviceInfoUpdateTime(now);
                deviceInfo.setMonitorDeviceInfoLastReportTime(now);
                deviceInfo.setMonitorDeviceInfoStatus(1); // 默认在线
                deviceInfo.setMonitorDeviceInfoMonitorEnabled(true); // 默认启用监控
                
                int result = deviceInfoMapper.insert(deviceInfo);
                log.info("新增设备信息成功: {} ({})", 
                    deviceInfo.getMonitorDeviceInfoDeviceName(), 
                    deviceInfo.getMonitorDeviceInfoIpAddress());
                return result > 0;
                
            } else {
                // 更新设备信息
                deviceInfo.setMonitorDeviceInfoId(existing.getMonitorDeviceInfoId());
                deviceInfo.setMonitorDeviceInfoCreateTime(existing.getMonitorDeviceInfoCreateTime());
                deviceInfo.setMonitorDeviceInfoUpdateTime(now);
                deviceInfo.setMonitorDeviceInfoLastReportTime(now);
                deviceInfo.setMonitorDeviceInfoStatus(1); // 更新为在线
                
                int result = deviceInfoMapper.updateById(deviceInfo);
                log.debug("更新设备信息成功: {}", deviceInfo.getMonitorDeviceInfoDeviceId());
                return result > 0;
            }
            
        } catch (Exception e) {
            log.error("保存或更新设备信息失败", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveDeviceMetricsByIpAndPort(String ipAddress, Integer port, MonitorDeviceMetrics metrics) {
        try {
            if (StringUtils.isBlank(ipAddress) || metrics == null) {
                log.warn("IP地址为空或指标数据为空，跳过保存");
                return false;
            }

            // 根据IP和端口查找或创建设备信息
            MonitorDeviceInfo deviceInfo = getDeviceInfoByIpAndPort(ipAddress, port);
            
            String deviceId;
            if (deviceInfo == null) {
                // 创建新设备
                deviceId = generateDeviceId(ipAddress, port);
                deviceInfo = new MonitorDeviceInfo();
                deviceInfo.setMonitorDeviceInfoDeviceId(deviceId);
                deviceInfo.setMonitorDeviceInfoDeviceName(metrics.getMonitorDeviceMetricsDeviceName() != null ? 
                    metrics.getMonitorDeviceMetricsDeviceName() : "Device-" + ipAddress);
                deviceInfo.setMonitorDeviceInfoIpAddress(ipAddress);
                deviceInfo.setMonitorDeviceInfoPort(port);
                deviceInfo.setMonitorDeviceInfoHostname(metrics.getMonitorDeviceMetricsHostname());
                deviceInfo.setMonitorDeviceInfoOsName(metrics.getMonitorDeviceMetricsOsName());
                deviceInfo.setMonitorDeviceInfoOsVersion(metrics.getMonitorDeviceMetricsOsVersion());
                deviceInfo.setMonitorDeviceInfoOsArch(metrics.getMonitorDeviceMetricsOsArch());
                
                saveOrUpdateDeviceInfo(deviceInfo);
                log.info("根据IP和端口创建新设备: {} ({}:{})", deviceId, ipAddress, port);
            } else {
                deviceId = deviceInfo.getMonitorDeviceInfoDeviceId();
            }

            // 设置设备ID并保存指标数据
            metrics.setMonitorDeviceMetricsDeviceId(deviceId);
            metrics.setMonitorDeviceMetricsIpAddress(ipAddress);
            metrics.setMonitorDeviceMetricsPort(port);
            
            return saveDeviceMetrics(metrics);
            
        } catch (Exception e) {
            log.error("根据IP和端口保存设备指标数据失败", e);
            return false;
        }
    }

    @Override
    public MonitorDeviceMetrics getLatestMetrics(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        return metricsMapper.selectLatestByDeviceId(deviceId);
    }

    @Override
    public MonitorDeviceInfo getDeviceInfo(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        return deviceInfoMapper.selectByDeviceId(deviceId);
    }

    @Override
    public MonitorDeviceInfo getDeviceInfoByIpAndPort(String ipAddress, Integer port) {
        if (StringUtils.isBlank(ipAddress)) {
            return null;
        }
        return deviceInfoMapper.selectByIpAndPort(ipAddress, port);
    }

    @Override
    public List<MonitorDeviceInfo> getOnlineDevices() {
        return deviceInfoMapper.selectOnlineDevices();
    }

    @Override
    public List<MonitorDeviceInfo> getMonitorEnabledDevices() {
        return deviceInfoMapper.selectMonitorEnabledDevices();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDeviceOnlineStatus(String deviceId, boolean online) {
        try {
            if (StringUtils.isBlank(deviceId)) {
                return false;
            }

            int status = online ? 1 : 0;
            LocalDateTime now = LocalDateTime.now();
            
            LambdaUpdateWrapper<MonitorDeviceInfo> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(MonitorDeviceInfo::getMonitorDeviceInfoDeviceId, deviceId)
                        .set(MonitorDeviceInfo::getMonitorDeviceInfoStatus, status)
                        .set(MonitorDeviceInfo::getMonitorDeviceInfoUpdateTime, now);
            
            if (online) {
                updateWrapper.set(MonitorDeviceInfo::getMonitorDeviceInfoLastOnlineTime, now);
            } else {
                updateWrapper.set(MonitorDeviceInfo::getMonitorDeviceInfoLastOfflineTime, now);
            }
            
            int result = deviceInfoMapper.update(null, updateWrapper);
            log.debug("更新设备在线状态: {} -> {}", deviceId, online ? "在线" : "离线");
            return result > 0;
            
        } catch (Exception e) {
            log.error("更新设备在线状态失败", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanExpiredMetrics(LocalDateTime beforeTime) {
        try {
            int count = metricsMapper.deleteBeforeTime(beforeTime);
            log.info("清理过期指标数据完成，删除记录数: {}", count);
            return count;
        } catch (Exception e) {
            log.error("清理过期指标数据失败", e);
            return 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleDevicePing(String deviceId, String ipAddress, Integer port) {
        try {
            if (StringUtils.isBlank(deviceId)) {
                deviceId = generateDeviceId(ipAddress, port);
            }

            // 更新设备在线状态和最后上报时间
            updateDeviceOnlineStatus(deviceId, true);
            updateDeviceLastReportTime(deviceId, LocalDateTime.now());
            
            log.debug("处理设备心跳: {} ({}:{})", deviceId, ipAddress, port);
            return true;
            
        } catch (Exception e) {
            log.error("处理设备心跳失败", e);
            return false;
        }
    }

    /**
     * 更新设备最后上报时间
     */
    private void updateDeviceLastReportTime(String deviceId, LocalDateTime reportTime) {
        try {
            LambdaUpdateWrapper<MonitorDeviceInfo> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(MonitorDeviceInfo::getMonitorDeviceInfoDeviceId, deviceId)
                        .set(MonitorDeviceInfo::getMonitorDeviceInfoLastReportTime, reportTime)
                        .set(MonitorDeviceInfo::getMonitorDeviceInfoUpdateTime, reportTime);
            
            deviceInfoMapper.update(null, updateWrapper);
        } catch (Exception e) {
            log.warn("更新设备最后上报时间失败: {}", deviceId, e);
        }
    }

    /**
     * 生成设备ID
     */
    private String generateDeviceId(String ipAddress, Integer port) {
        return "device-" + ipAddress.replace(".", "-") + 
               (port != null && port > 0 ? "-" + port : "") + 
               "-" + System.currentTimeMillis();
    }
}
