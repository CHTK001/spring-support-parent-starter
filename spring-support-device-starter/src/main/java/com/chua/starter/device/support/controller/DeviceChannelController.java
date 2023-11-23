package com.chua.starter.device.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.device.support.entity.DeviceChannel;
import com.chua.starter.device.support.entity.DeviceDict;
import com.chua.starter.device.support.service.DeviceChannelService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import lombok.AllArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备传感器/管道控制器
 *
 * @author CH
 */
@RestController
@AllArgsConstructor
@RequestMapping("v1/device/channel")
public class DeviceChannelController {

    private final DeviceChannelService deviceChannelService;

    private final TransactionTemplate transactionTemplate;
    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("list")
    public ReturnResult<List<DeviceChannel>> list(Integer deviceId) {
        return ReturnResult.ok(deviceChannelService.list(Wrappers.<DeviceChannel>lambdaQuery().eq(DeviceChannel::getDeviceId, deviceId)));
    }

    /**
     * 分页
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("page")
    public ReturnPageResult<DeviceChannel> page(
            Integer deviceId,
            @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return PageResultUtils.ok(deviceChannelService.page(new Page<DeviceChannel>(pageNum, pageSize), Wrappers.<DeviceChannel>lambdaQuery().eq(DeviceChannel::getDeviceId, deviceId)));
    }

    /**
     * 保存
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PostMapping("save")
    public ReturnResult<Boolean> save(@RequestBody @Validated({AddGroup.class}) List<DeviceChannel> deviceChannel, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        if(CollectionUtils.isEmpty(deviceChannel)) {
            return ReturnResult.ok(true);
        }

        List<DeviceChannel> newDeviceChannel = deviceChannel.stream().filter(it -> null != (it.getDeviceId())).collect(Collectors.toList());
        for (DeviceChannel channel : newDeviceChannel) {
            channel.setCreateTime(new Date());
        }

        transactionTemplate.execute(status -> {
            deviceChannelService.remove(Wrappers.<DeviceChannel>lambdaQuery().eq(DeviceChannel::getDeviceId, newDeviceChannel.get(0).getDeviceId()));
            deviceChannelService.saveBatch(newDeviceChannel);
            return true;
        });
        return ReturnResult.ok(true);
    }

    /**
     * 更新
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PutMapping("update")
    public ReturnResult<DeviceChannel> update(@RequestBody @Validated({UpdateGroup.class}) DeviceChannel deviceChannel, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        deviceChannelService.updateById(deviceChannel);
        return ReturnResult.ok(deviceChannel);
    }

    /**
     * 删除
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(String id) {
        if (StringUtils.isBlank(id)) {
            return ReturnResult.illegal("删除信息不存在");
        }

        deviceChannelService.removeBatchByIds(Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id));
        return ReturnResult.ok(true);
    }
}
