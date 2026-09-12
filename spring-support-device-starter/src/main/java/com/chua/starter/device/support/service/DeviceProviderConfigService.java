package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.spi.model.DeviceConnectInfo;

import java.util.List;
import java.util.Map;

/**
* 提供商配置接口
* <p>每个提供商可自定义离线检测时间（秒），并提供检测逻辑。</p>
*
* @since 4.0.0.42
* @author CH
 */
public interface DeviceProviderConfigService {

    /**
    * 获取提供商默认离线检测阈值（秒）
    *
    * @param vendor 厂商标识
    * @return 阈值（秒），默认 60
     */
    int getDefaultOfflineThreshold(String vendor);

    /**
    * 获取所有厂商的离线检测阈值配置
    *
    * @return 厂商标识 -> 阈值（秒）
     */
    Map<String, Integer> getThresholds();

    /**
    * 更新指定厂商的离线检测阈值（秒）
    *
    * @param vendor    厂商标识
    * @param threshold 阈值（秒）
     */
    void updateThreshold(String vendor, int threshold);

    /**
    * 检测设备是否在线
    *
    * @param device 设备信息
    * @param info   连接信息
    * @return 是否在线
     */
    boolean checkOnline(DeviceInfo device, DeviceConnectInfo info);

    /**
    * 获取所有需要检测的设备
    *
    * @return 设备列表
     */
    List<DeviceInfo> listCheckableDevices();
}