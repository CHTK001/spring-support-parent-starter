package com.chua.starter.device.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.device.support.adaptor.Adaptor;
import com.chua.starter.device.support.entity.DeviceCloudPlatform;
import com.chua.starter.device.support.entity.DeviceDict;
import com.chua.starter.device.support.entity.DeviceManufacturer;
import com.chua.starter.device.support.service.DeviceCloudPlatformService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 厂家信息控制器
 * @author CH
 */
@RestController
@AllArgsConstructor
@RequestMapping("v1/device/cloud/platform")
public class DeviceCloudPlatformController {

    private final DeviceCloudPlatformService cloudPlatformService;

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("list")
    public ReturnResult<List<DeviceCloudPlatform>> list() {
        return ReturnResult.ok(cloudPlatformService.list());
    }
    /**
     * 分页
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("page")
    public ReturnPageResult<DeviceCloudPlatform> page(
                                                   @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        ReturnPageResult<DeviceCloudPlatform> manufacturerName = PageResultUtils.ok(cloudPlatformService.page(new Page<DeviceCloudPlatform>(pageNum, pageSize),
                new MPJLambdaWrapper<DeviceCloudPlatform>()
                        .selectAll(DeviceCloudPlatform.class)
                        .selectAs(DeviceManufacturer::getManufacturerName, "manufacturerName")
                        .innerJoin(DeviceManufacturer.class, DeviceManufacturer::getManufacturerId, DeviceCloudPlatform::getManufacturerId)
        ));

        Map<String, Class<Adaptor>> stringClassMap = ServiceProvider.of(Adaptor.class).listType();
        for (DeviceCloudPlatform datum : manufacturerName.getData().getData()) {
            datum.setExistImplInterface(stringClassMap.containsKey(datum.getDevicePlatformCode()));
        }
        return manufacturerName;
    }
    /**
     * 保存
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PostMapping("save")
    public ReturnResult<DeviceCloudPlatform> save(@RequestBody @Validated({AddGroup.class}) DeviceCloudPlatform deviceCloudPlatform, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        cloudPlatformService.save(deviceCloudPlatform);
        return ReturnResult.ok(deviceCloudPlatform);
    }
    /**
     * 更新
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PutMapping("update")
    public ReturnResult<DeviceCloudPlatform> update(@RequestBody @Validated({UpdateGroup.class}) DeviceCloudPlatform deviceCloudPlatform, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        cloudPlatformService.updateById(deviceCloudPlatform);
        return ReturnResult.ok(deviceCloudPlatform);
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

        cloudPlatformService.removeBatchByIds(Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id));
        return ReturnResult.ok(true);
    }
}
