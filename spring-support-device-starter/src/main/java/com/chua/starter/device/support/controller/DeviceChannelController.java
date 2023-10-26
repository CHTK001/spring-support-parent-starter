package com.chua.starter.device.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.device.support.entity.DeviceChannel;
import com.chua.starter.device.support.entity.DeviceDict;
import com.chua.starter.device.support.service.DeviceChannelService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 设备传感器/管道控制器
 * @author CH
 */
@RestController
@AllArgsConstructor
@RequestMapping("v1/device/channel")
public class DeviceChannelController {

    private final DeviceChannelService deviceChannelService;

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("list")
    public ReturnResult<List<DeviceChannel>> list() {
        return ReturnResult.ok(deviceChannelService.list());
    }

    /**
     * 分页
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("page")
    public ReturnPageResult<DeviceChannel> page(
                                                   @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return PageResultUtils.ok(deviceChannelService.page(new Page<DeviceChannel>(pageNum, pageSize), Wrappers.<DeviceChannel>lambdaQuery()));
    }
    /**
     * 保存
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PostMapping("save")
    public ReturnResult<DeviceChannel> save(@RequestBody @Validated({AddGroup.class}) DeviceChannel deviceChannel, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        deviceChannel.setCreateTime(new Date());
        deviceChannelService.save(deviceChannel);
        return ReturnResult.ok(deviceChannel);
    }
    /**
     * 更新
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PutMapping("update")
    public ReturnResult<DeviceChannel> update(@RequestBody @Validated({UpdateGroup.class}) DeviceChannel deviceChannel, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
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
        if(StringUtils.isBlank(id)) {
            return ReturnResult.illegal("删除信息不存在");
        }

        deviceChannelService.removeBatchByIds(Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id));
        return ReturnResult.ok(true);
    }
}
