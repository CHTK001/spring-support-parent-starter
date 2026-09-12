package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.entity.DeviceChannel;
import com.chua.starter.device.support.service.DeviceChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
   * 通道管理 控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/device/channel")
@RequiredArgsConstructor
public class ChannelController {

    /**
     * 通道服务
     */
    private final DeviceChannelService channelService;

    /**
     * 添加通道
     * @param channel 通道
     * @return 添加通道的结果
     */
    @PostMapping
    public DeviceChannel addChannel(@RequestBody DeviceChannel channel) {
        return channelService.addChannel(channel);
    }

    /**
     * 更新通道
     * @param id 标识
     * @param channel 通道
     */
    @PutMapping("/{id}")
    public void updateChannel(@PathVariable String id, @RequestBody DeviceChannel channel) {
        channelService.updateChannel(id, channel);
    }

    /**
     * 删除通道
     * @param id 标识
     */
    @DeleteMapping("/{id}")
    public void deleteChannel(@PathVariable String id) {
        channelService.deleteChannel(id);
    }

    /**
     * 获取通道
     * @param id 标识
     * @return 获取通道的结果
     */
    @GetMapping("/{id}")
    public DeviceChannel getChannel(@PathVariable String id) {
        return channelService.getChannel(id);
    }

    /**
     * 查询设备的通道列表
     * @param deviceId deviceid
     * @return 列表通道的结果
     */
    @GetMapping("/list")
    public List<DeviceChannel> listChannels(@RequestParam String deviceId) {
        return channelService.listChannels(deviceId);
    }

    /**
     * 启用/禁用通道
     * @param id 标识
     * @param status 状态
     */
    @PutMapping("/{id}/status")
    public void toggleStatus(@PathVariable String id, @RequestParam String status) {
        channelService.toggleStatus(id, status);
    }
}