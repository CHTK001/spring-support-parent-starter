package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceChannel;

import java.util.List;

/**
 * 设备通道服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DeviceChannelService {

    /**
     * 添加通道
     *
     * @param channel 通道实体
     * @return 通道实体
     */
    DeviceChannel addChannel(DeviceChannel channel);

    /**
     * 更新通道
     *
     * @param id      通道 标识
     * @param channel 通道实体
     */
    void updateChannel(String id, DeviceChannel channel);

    /**
     * 删除通道
     *
     * @param id 通道 标识
     */
    void deleteChannel(String id);

    /**
     * 获取通道
     *
     * @param id 通道 标识
     * @return 通道实体
     */
    DeviceChannel getChannel(String id);

    /**
     * 查询设备的通道列表
     *
     * @param deviceId 设备 标识
     * @return 通道列表
     */
    List<DeviceChannel> listChannels(String deviceId);

    /**
     * 启用/禁用通道
     *
     * @param id     通道 标识
     * @param status 状态：已启用、已禁用
     */
    void toggleStatus(String id, String status);
}