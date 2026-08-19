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
     * 通道 Mapper
     */
    private final DeviceChannelMapper channelMapper;

    @Override
    public DeviceChannel addChannel(DeviceChannel channel) {
        channelMapper.insert(channel);
        log.info("添加通道: deviceId={}, no={}", channel.getDeviceChannelDeviceId(), channel.getDeviceChannelNo());
        return channel;
    }

    @Override
    public void updateChannel(String id, DeviceChannel channel) {
        channel.setDeviceChannelId(id);
        channelMapper.updateById(channel);
    }

    @Override
    public void deleteChannel(String id) {
        channelMapper.deleteById(id);
    }

    @Override
    public DeviceChannel getChannel(String id) {
        return channelMapper.selectById(id);
    }

    @Override
    public List<DeviceChannel> listChannels(String deviceId) {
        LambdaQueryWrapper<DeviceChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceChannel::getDeviceChannelDeviceId, deviceId);
        wrapper.orderByAsc(DeviceChannel::getDeviceChannelSort);
        return channelMapper.selectList(wrapper);
    }

    @Override
    public void toggleStatus(String id, String status) {
        DeviceChannel channel = new DeviceChannel();
        channel.setDeviceChannelId(id);
        channel.setDeviceChannelStatus(status);
        channelMapper.updateById(channel);
    }
}