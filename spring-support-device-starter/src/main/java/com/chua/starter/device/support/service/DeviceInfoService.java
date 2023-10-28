package com.chua.starter.device.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;

import java.util.List;

/**
 * @author CH
 */
public interface DeviceInfoService extends IService<DeviceInfo> {


    /**
     * 注册装置
     *
     * @param deviceInfos            设备信息
     * @param cloudPlatformConnector 云平台连接器
     * @param result                 后果
     */
    void registerDevice(List<DeviceInfo> deviceInfos, DeviceCloudPlatformConnector cloudPlatformConnector, StaticResult result);

    /**
     * 页
     *
     * @param keyword  关键字
     * @param pageNum  书籍页码
     * @param pageSize 页面大小
     * @return {@link Page}<{@link DeviceInfo}>
     */
    Page<DeviceInfo> page(Integer pageNum, Integer pageSize, String keyword);

    /**
     * 获取装置信息
     *
     * @param deviceId   设备id
     * @param deviceIsmi 处女主义
     * @return {@link DeviceInfo}
     */
    DeviceInfo getDeviceInfo(String deviceId, String deviceIsmi);
}
