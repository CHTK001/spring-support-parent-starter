package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceOrganization;
import com.chua.starter.device.spi.model.DeviceConnectInfo;

import java.util.List;

/**
* 设备服务接口
*
* @since 4.0.0.42
* @author CH
 */
public interface DeviceService {

    /**
    * 添加设备并连接
    *
    * @param info 设备连接信息
    * @return 设备信息实体
    */
    DeviceInfo addDevice(DeviceConnectInfo info);

    /**
    * 更新设备信息
    *
    * @param id   设备 标识
    * @param info 设备信息
    */
    void updateDevice(String id, DeviceInfo info);

    /**
    * 删除设备并断开连接
    *
    * @param id 设备 标识
    */
    void deleteDevice(String id);

    /**
    * 获取设备详情
    *
    * @param id 设备 标识
    * @return 设备信息
    */
    DeviceInfo getDevice(String id);

    /**
    * 按组织查询设备列表
    *
    * @param orgId 组织 标识
    * @return 设备列表
    */
    List<DeviceInfo> listDevices(String orgId);

    /**
    * 按大类查询设备列表
    *
    * @param category 大类，例如 摄像头、accesscontrol
    * @return 设备列表
    */
    List<DeviceInfo> listByCategory(String category);

    /**
    * 同步组织树
    * <p>从各厂商平台拉取最新组织树，更新本地数据库。</p>
    */
    void syncOrganization();

    /**
    * 获取组织树
    *
    * @param vendor 厂商，空 表示全部
    * @return 组织列表
    */
    List<DeviceOrganization> getOrganizationTree(String vendor);

    /**
    * 更新设备离线检测阈值（秒）
    *
    * @param id        设备 标识
    * @param threshold 阈值（秒）
    */
    void updateOfflineThreshold(String id, int threshold);

    /**
    * 重新连接设备：关闭旧会话、按现有连接信息创建新会话并注册。
    *
    * @param id 设备 标识
    */
    void reconnect(String id);

    /**
    * 刷新设备状态：探测会话并更新数据库 online 字段。
    *
    * @param id 设备 标识
    */
    void refreshStatus(String id);

    /**
    * 测试连接：根据设备 标识 取出连接信息并发起一次 连接，
    * <p>用于"添加设备前"或"重连前"的连通性探测。</p>
    *
    * @param info 设备连接信息
    * @return 是否可达
    */
    boolean testConnection(DeviceConnectInfo info);
}
