package com.chua.starter.device.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.device.support.entity.DeviceDict;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceType;
import com.chua.starter.device.support.service.DeviceInfoService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.query.MPJLambdaQueryWrapper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 厂家信息控制器
 *
 * @author CH
 */
@RestController
@AllArgsConstructor
@RequestMapping("v1/device/device")
public class DeviceInfoController {

    private final DeviceInfoService deviceInfoService;

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("list")
    public ReturnResult<List<DeviceInfo>> list(String deviceTypeId, String deviceTypeCode) {
        return ReturnResult.ok(deviceInfoService.list(new MPJLambdaWrapper<DeviceInfo>()
                        .selectAll(DeviceInfo.class)
                        .leftJoin(DeviceType.class, DeviceType::getDeviceTypeId, DeviceInfo::getDeviceTypeId)
                .eq(StringUtils.isNotBlank(deviceTypeId), DeviceInfo::getDeviceTypeId, deviceTypeId)
                .eq(StringUtils.isNotBlank(deviceTypeCode), DeviceType::getDeviceTypeCode, deviceTypeCode)
        ));
    }

    /**
     * 分页
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("page")
    public ReturnPageResult<DeviceInfo> page(
            String keyword,
            String deviceType,
            @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        return PageResultUtils.ok(deviceInfoService.page(pageNum, pageSize, keyword, deviceType));
    }

    /**
     * 保存
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PostMapping("save")
    public ReturnResult<DeviceInfo> save(@RequestBody @Validated({AddGroup.class}) DeviceInfo deviceInfo, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        deviceInfo.setCreateTime(new Date());
        deviceInfoService.save(deviceInfo);
        return ReturnResult.ok(deviceInfo);
    }

    /**
     * 更新
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PutMapping("update")
    public ReturnResult<DeviceInfo> update(@RequestBody @Validated({UpdateGroup.class}) DeviceInfo deviceInfo, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        deviceInfoService.updateById(deviceInfo);
        return ReturnResult.ok(deviceInfo);
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

        deviceInfoService.removeBatchByIds(Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id));
        return ReturnResult.ok(true);
    }
}
