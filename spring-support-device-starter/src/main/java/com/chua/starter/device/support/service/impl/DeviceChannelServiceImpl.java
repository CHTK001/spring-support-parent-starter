package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceChannel;
import com.chua.starter.device.support.mapper.DeviceChannelMapper;
import com.chua.starter.device.support.service.DeviceChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备通道服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceChannelServiceImpl implements DeviceChannelService {

    /**
     * 通道 映射器
     */
    private final DeviceChannelMapper channelMapper;

    @Override
    /**
     * 添加通道
     * @param channel 参数
     * @return 返回值
    */
    public DeviceChannel addChannel(DeviceChannel channel) {
        channelMapper.insert(channel);
        log.info("添加通道: deviceId={}, no={}", channel.getDeviceChannelDeviceId(), channel.getDeviceChannelNo());
        return channel;
    }

    @Override
    /**
     * 更新通道
     * @param channel 参数
     * @param id 字符串
     * @return 返回值
    */
    public void updateChannel(String id, DeviceChannel channel) {
        channel.setDeviceChannelId(id);
        channelMapper.updateById(channel);
    }

    @Override
    /**
     * 删除通道
     * @param id 字符串
     * @return 返回值
    */
    public void deleteChannel(String id) {
        channelMapper.deleteById(id);
    }

    @Override
    /**
     * 获取通道
     * @param id 字符串
     * @return 返回值
    */
    public DeviceChannel getChannel(String id) {
        return channelMapper.selectById(id);
    }

    @Override
    /**
     * 列表通道
     * @param deviceId 字符串
     * @return 返回值
    */
    public List<DeviceChannel> listChannels(String deviceId) {
        LambdaQueryWrapper<DeviceChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceChannel::getDeviceChannelDeviceId, deviceId);
        wrapper.orderByAsc(DeviceChannel::getDeviceChannelSort);
        return channelMapper.selectList(wrapper);
    }

    @Override
    /**
     * toggle状态
     * @param id 字符串
     * @param status 字符串
     * @return 返回值
    */
    public void toggleStatus(String id, String status) {
        DeviceChannel channel = new DeviceChannel();
        channel.setDeviceChannelId(id);
        channel.setDeviceChannelStatus(status);
        channelMapper.updateById(channel);
    }
}
