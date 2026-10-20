package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.model.DeviceVendorDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * 设备厂商元数据控制器
 * <p>把 {@link DeviceProvider#descriptor()} 暴露给前端：支持的厂家、接入方式、
 * 平台、凭据字段与能力位。前端据此动态渲染“厂商接入”页，新增厂商无需改前端。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/device/provider")
public class ProviderController {

    /**
     * 所有已注册的厂商（Spring Bean）
     */
    private final List<DeviceProvider> providers;

    /**
     * 查询所有厂商描述符
     *
     * @return 按厂商标识排序的描述符
     */
    @GetMapping("/vendors")
    public ReturnResult<List<DeviceVendorDescriptor>> vendors() {
        return ReturnResult.ok(providers.stream()
                .map(DeviceProvider::descriptor)
                .sorted(Comparator.comparing(DeviceVendorDescriptor::vendor))
                .toList());
    }

    /**
     * 查询单个厂商描述符
     *
     * @param vendor 厂商标识
     * @return 描述符；未注册时返回 404
     */
    @GetMapping("/vendors/{vendor}")
    public ReturnResult<DeviceVendorDescriptor> vendor(@PathVariable String vendor) {
        return ReturnResult.ok(providers.stream()
                .filter(p -> p.getVendor().equalsIgnoreCase(vendor))
                .findFirst()
                .map(DeviceProvider::descriptor)
                .orElseThrow(() -> new IllegalArgumentException("未注册的设备厂商: " + vendor)));
    }
}
