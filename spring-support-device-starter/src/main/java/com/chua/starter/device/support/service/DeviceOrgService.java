package com.chua.starter.device.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceOrg;

import java.util.List;

/**
 *    
 * @author CH
 */     
public interface DeviceOrgService extends IService<DeviceOrg>{


    /**
     * 注册org
     *
     * @param platformConnector 平台连接器
     * @param deviceOrgs        设备组织
     */
    void registerOrg(List<DeviceOrg> deviceOrgs, DeviceCloudPlatformConnector platformConnector);
    }
