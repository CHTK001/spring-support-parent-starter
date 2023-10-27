package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceLog;
import com.chua.starter.device.support.entity.DeviceOrg;
import com.chua.starter.device.support.mapper.DeviceOrgMapper;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.service.DeviceLogService;
import com.chua.starter.device.support.service.DeviceOrgService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 *    
 * @author CH
 */     
@Service
public class DeviceOrgServiceImpl extends ServiceImpl<DeviceOrgMapper, DeviceOrg> implements DeviceOrgService{
    public static final ExecutorService STATIC_EXECUTOR_SERVICE = ThreadUtils.newFixedThreadExecutor(100);
    @Resource
    private DeviceLogService deviceLogService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Override
    public void registerOrg(List<DeviceOrg> deviceOrgs, DeviceCloudPlatformConnector platformConnector, StaticResult result) {
        if(CollectionUtils.isEmpty(deviceOrgs)) {
            return;
        }

        transactionTemplate.execute(status -> {
            String connectorId = platformConnector.getDeviceConnectorId() + "";
            registerService(deviceOrgs, platformConnector);
            result.addTotal(deviceOrgs.size());
            unregisterOrg(connectorId);
            registerOrg(connectorId, deviceOrgs, result);
            return true;
        });

    }

    private void registerOrg(String connectorId, List<DeviceOrg> deviceOrgs, StaticResult result) {
        for (DeviceOrg child : deviceOrgs) {
            DeviceLog deviceLog = new DeviceLog();
            deviceLog.setDeviceLogFrom("同步组织接口(页面)");
            deviceLog.setCreateTime(new Date());
            deviceLog.setDeviceLogType("SYNC("+ connectorId +")");
            try {
                saveOrUpdate(child, Wrappers.<DeviceOrg>lambdaUpdate()
                        .eq(DeviceOrg::getDeviceOrgTreeId, child.getDeviceOrgTreeId())
                );
                result.addSuccessTotal(1);
            } catch (Exception e) {
                result.addFailureTotal(1);
                deviceLog.setDeviceLogError(e.getLocalizedMessage());
            }
            deviceLogService.save(deviceLog);
        }
    }

    /**
     * 注销org
     *
     * @param connectorId 连接器id
     */
    private void unregisterOrg(String connectorId) {
        remove(Wrappers.<DeviceOrg>lambdaQuery()
                .eq(DeviceOrg::getDeviceConnectorId, connectorId)
                .ne(DeviceOrg::getDeviceOrgTreeId, connectorId)
        );
    }

    /**
     * 注册服务
     *
     * @param deviceOrgs        设备组织
     * @param platformConnector 平台连接器
     */
    private void registerService(List<DeviceOrg> deviceOrgs, DeviceCloudPlatformConnector platformConnector) {
        String connectorId = platformConnector.getDeviceConnectorId() + "";
        DeviceOrg deviceOrg = new DeviceOrg();
        deviceOrg.setDeviceOrgPid("-1");
        deviceOrg.setDeviceOrgPath("0");
        deviceOrg.setDeviceOrgTreeId(connectorId);
        deviceOrg.setDeviceConnectorId(connectorId);
        deviceOrg.setCreateTime(new Date());
        deviceOrg.setDeviceOrgName(platformConnector.getDeviceConnectorName());

        deviceOrgs.add(deviceOrg);
    }
}
