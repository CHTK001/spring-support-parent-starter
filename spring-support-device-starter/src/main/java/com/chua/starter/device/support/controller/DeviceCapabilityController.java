package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.provider.sdk.DeviceCapabilityMatrix;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.model.DeviceCapability;
import com.chua.starter.device.spi.model.DeviceVendorDescriptor;
import com.chua.starter.device.spi.model.PlatformDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台能力矩阵控制器。
 *
 * <p>前端加载一次能力矩阵后缓存，按「厂商 + 平台」决定功能入口的显隐：
 * 平台支持的显示，不支持的隐藏（而不是显示后点了报错）。
 * 不支持的平台会带上原因，便于在界面上给出「该平台不支持组织架构」这类说明。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/device/capability")
public class DeviceCapabilityController {

    /**
     * 所有已注册的厂商（Spring Bean）
     */
    private final List<DeviceProvider> providers;

    /**
     * 全部平台的能力矩阵。
     *
     * @return 平台标识到能力矩阵的映射
     */
    @GetMapping("/matrix")
    public ReturnResult<Map<String, DeviceCapabilityMatrix>> matrix() {
        Map<String, DeviceCapabilityMatrix> result = new LinkedHashMap<>();
        DeviceCapabilityMatrix.Registry.all().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return ReturnResult.ok(result);
    }

    /**
     * 单个平台的能力矩阵。
     *
     * @param platform 平台标识，形如 hikvision.ezviz
     * @return 能力矩阵；未登记时抛参数异常
     */
    @GetMapping("/matrix/{platform}")
    public ReturnResult<DeviceCapabilityMatrix> platform(@PathVariable String platform) {
        DeviceCapabilityMatrix matrix = DeviceCapabilityMatrix.Registry.get(platform);
        if (matrix == null) {
            throw new IllegalArgumentException("未登记的设备平台: " + platform);
        }
        return ReturnResult.ok(matrix);
    }

    /**
     * 全部能力位的字典（编码 + 展示名），供前端渲染能力标签。
     *
     * @return 能力位字典
     */
    @GetMapping("/capabilities")
    public ReturnResult<List<Map<String, String>>> capabilities() {
        List<Map<String, String>> result = new ArrayList<>();
        for (DeviceCapability capability : DeviceCapability.values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", capability.name());
            item.put("name", capability.getDescription());
            result.add(item);
        }
        return ReturnResult.ok(result);
    }

    /**
     * 前端入口：按厂商给出「每个平台支持哪些功能」，直接可用于显隐判断。
     *
     * <p>返回结构为 vendor → platformCode → 该平台的能力位编码列表。
     * 与 {@code /api/device/provider/vendors} 的平台级 capabilities 同源，
     * 后者适合在表单页直接用，本接口适合做全局功能开关表。</p>
     *
     * @return 厂商到平台能力位的映射
     */
    @GetMapping("/by-vendor")
    public ReturnResult<Map<String, Map<String, List<String>>>> byVendor() {
        Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();
        providers.stream()
                .map(DeviceProvider::descriptor)
                .sorted(Comparator.comparing(DeviceVendorDescriptor::vendor))
                .forEach(descriptor -> {
                    Map<String, List<String>> platforms = new LinkedHashMap<>();
                    for (PlatformDescriptor platform : descriptor.platforms()) {
                        platforms.put(platform.code(), platform.capabilities().stream()
                                .map(DeviceCapability::name)
                                .sorted()
                                .toList());
                    }
                    result.put(descriptor.vendor(), platforms);
                });
        return ReturnResult.ok(result);
    }
}
