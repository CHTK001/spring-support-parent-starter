package com.chua.starter.device.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.device.support.entity.DeviceDict;
import com.chua.starter.device.support.entity.DeviceManufacturer;
import com.chua.starter.device.support.service.DeviceManufacturerService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 厂家信息控制器
 * @author CH
 */
@RestController
@AllArgsConstructor
@RequestMapping("v1/device/manufacturer")
public class DeviceManufacturerController {

    private final DeviceManufacturerService deviceManufacturerService;

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("list")
    public ReturnResult<List<DeviceManufacturer>> list() {
        return ReturnResult.ok(deviceManufacturerService.list());
    }
    /**
     * 分页
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("page")
    public ReturnPageResult<DeviceManufacturer> page(
                                                   @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return PageResultUtils.ok(deviceManufacturerService.page(new Page<DeviceManufacturer>(pageNum, pageSize), Wrappers.<DeviceManufacturer>lambdaQuery()));
    }
    /**
     * 保存
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PostMapping("save")
    public ReturnResult<DeviceManufacturer> save(@RequestBody @Validated({AddGroup.class}) DeviceManufacturer deviceManufacturer, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        if(deviceManufacturerService.count(Wrappers.<DeviceManufacturer>lambdaQuery()
                .eq(DeviceManufacturer::getManufacturerName, deviceManufacturer.getManufacturerName())
                .or().eq(DeviceManufacturer::getManufacturerCode, deviceManufacturer.getManufacturerCode())
        ) > 0 ) {
            return ReturnResult.illegal("厂商名称/编号已存在");
        }
        deviceManufacturerService.save(deviceManufacturer);
        return ReturnResult.ok(deviceManufacturer);
    }
    /**
     * 更新
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PutMapping("update")
    public ReturnResult<DeviceManufacturer> update(@RequestBody @Validated({UpdateGroup.class}) DeviceManufacturer deviceManufacturer, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        if(deviceManufacturerService.count(Wrappers.<DeviceManufacturer>lambdaQuery()
                        .ne(DeviceManufacturer::getManufacturerId, deviceManufacturer.getManufacturerId())
                        .and(deviceManufacturerLambdaQueryWrapper -> deviceManufacturerLambdaQueryWrapper.eq(DeviceManufacturer::getManufacturerName, deviceManufacturer.getManufacturerName())
                                .or().eq(DeviceManufacturer::getManufacturerCode, deviceManufacturer.getManufacturerCode()))

        ) > 0 ) {
            return ReturnResult.illegal("厂商名称/编号已存在");
        }
        deviceManufacturerService.updateById(deviceManufacturer);
        return ReturnResult.ok(deviceManufacturer);
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

        deviceManufacturerService.removeBatchByIds(Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id));
        return ReturnResult.ok(true);
    }
}
