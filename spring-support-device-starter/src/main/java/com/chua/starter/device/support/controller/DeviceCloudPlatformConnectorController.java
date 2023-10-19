package com.chua.starter.device.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDict;
import com.chua.starter.device.support.service.DeviceCloudPlatformConnectorService;
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
@RequestMapping("v1/device/cloud/platform/connector")
public class DeviceCloudPlatformConnectorController {

    private final DeviceCloudPlatformConnectorService deviceCloudPlatformConnectorService;

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("list")
    public ReturnResult<List<DeviceCloudPlatformConnector>> list(String devicePlatformId) {
        return ReturnResult.ok(deviceCloudPlatformConnectorService.list(Wrappers.<DeviceCloudPlatformConnector>lambdaQuery()
                .eq(DeviceCloudPlatformConnector::getDevicePlatformId, devicePlatformId)
        ));
    }
    /**
     * 分页
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("page")
    public ReturnPageResult<DeviceCloudPlatformConnector> page(
                                                String devicePlatformId,
                                                   @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return PageResultUtils.ok(deviceCloudPlatformConnectorService.page(new Page<DeviceCloudPlatformConnector>(pageNum, pageSize),
                Wrappers.<DeviceCloudPlatformConnector>lambdaQuery()
                        .eq(DeviceCloudPlatformConnector::getDevicePlatformId, devicePlatformId)
        ));
    }
    /**
     * 保存
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PostMapping("save")
    public ReturnResult<DeviceCloudPlatformConnector> save(@RequestBody @Validated({AddGroup.class}) DeviceCloudPlatformConnector deviceCloudPlatformConnector, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        deviceCloudPlatformConnectorService.save(deviceCloudPlatformConnector);
        return ReturnResult.ok(deviceCloudPlatformConnector);
    }
    /**
     * 更新
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PutMapping("update")
    public ReturnResult<DeviceCloudPlatformConnector> update(@RequestBody @Validated({UpdateGroup.class}) DeviceCloudPlatformConnector deviceCloudPlatformConnector, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        deviceCloudPlatformConnectorService.updateById(deviceCloudPlatformConnector);
        return ReturnResult.ok(deviceCloudPlatformConnector);
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

        deviceCloudPlatformConnectorService.removeBatchByIds(Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id));
        return ReturnResult.ok(true);
    }
}
