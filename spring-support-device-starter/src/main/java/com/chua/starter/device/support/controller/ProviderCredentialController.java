package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.scheduler.DeviceScheduleManager;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSyncService;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供商接入凭据控制器
 * <p>管理厂商自定义的 AK/SK 等接入参数；同一厂商可登记多套凭据。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
// 说明：厂商 AK/SK 等接入参数的登记与维护（本模块未引入 swagger 注解）
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/device/provider-credential")
public class ProviderCredentialController {

    /** 提供商接入凭据服务 */
    private final DeviceProviderCredentialService credentialService;

    /** 设备服务（凭据连通性测试） */
    private final DeviceService deviceService;

    /** 凭据维度同步服务 */
    private final DeviceSyncService deviceSyncService;

    /** 设备定时调度器（凭据级 Tick 开关/间隔热重载） */
    private final DeviceScheduleManager scheduleManager;

    @GetMapping("/list")
    /**
     * Request Param
     * @param vendor 字符串
     * @return 返回值
    */
    public ReturnResult<List<DeviceProviderCredential>> list(@RequestParam(required = false) String vendor) {
        return ReturnResult.ok(credentialService.list(vendor));
    }
    @GetMapping("/{id}")
    /**
     * detail
     * @param id 字符串
     * @return 返回值
    */
    public ReturnResult<DeviceProviderCredential> detail(@PathVariable String id) {
        return ReturnResult.ok(credentialService.get(id));
    }
    @PostMapping
    /**
     * 创建
     * @param credential 参数
     * @return 返回值
    */
    public ReturnResult<DeviceProviderCredential> create(@RequestBody DeviceProviderCredential credential) {
        return ReturnResult.ok(credentialService.create(credential));
    }
    @PutMapping("/{id}")
    public ReturnResult<DeviceProviderCredential> update(@PathVariable String id,
                                                         @RequestBody DeviceProviderCredential credential) {
        DeviceProviderCredential updated = credentialService.update(id, credential);
        // 高级配置（开关/间隔）变更后：重扫该凭据的 Tick，下一轮调度生效
        scheduleManager.refresh(id);
        return ReturnResult.ok(updated);
    }

    /**
     * 只切换启用/停用，避免整对象更新把 AK/SK、默认套等字段一并覆盖。
     *
     * @param id      凭据 ID
     * @param enabled 是否启用
     * @return 更新后的凭据
     */
    @PutMapping("/{id}/enabled")
    public DeviceProviderCredential updateEnabled(@PathVariable String id,
                                                 @RequestParam boolean enabled) {
        return credentialService.updateEnabled(id, enabled);
    }

    @DeleteMapping("/{id}")
    /**
     * 删除
     * @param id 字符串
     * @return 返回值
    */
    public ReturnResult<Void> delete(@PathVariable String id) {
        credentialService.delete(id);
        return ReturnResult.ok();
    }

    /**
     * 测试凭据连通性
     *
     * <p>只验证平台登录/令牌，不需要具体设备；SDK 直连凭据没有 IP，始终返回 false。</p>
     *
     * @param id 凭据 ID
     * @return true 表示登录成功
     */
    @PostMapping("/{id}/test")
    public ReturnResult<Boolean> test(@PathVariable String id) {
        DeviceProviderCredential credential = credentialService.resolveById(id);
        if (credential == null) {
            throw new IllegalArgumentException("凭据不存在: " + id);
        }
        DeviceConnectInfo info = new DeviceConnectInfo(
                credential.getDeviceCredentialVendor(),
                "PLATFORM",
                null,
                0,
                null,
                null,
                credential.getDeviceCredentialPlatformType(),
                credential.getDeviceCredentialAppKey(),
                credential.getDeviceCredentialAppSecret(),
                null,
                null);
        return ReturnResult.ok(deviceService.testConnection(info));
    }

    /**
     * 按凭据同步设备目录（拉取方向；落库 device_info）
     *
     * <p>以凭据 ID 为维度建连，只拉取该凭据名下设备目录，按 vendor + 厂商侧标识
     * 幂等 upsert；设备台账行回写 {@code deviceInfoCredentialId}。</p>
     *
     * @param id 凭据 ID
     * @return created / updated / skipped 统计
     */
    @PostMapping("/{id}/sync/devices")
    public ReturnResult<Map<String, Integer>> syncDevices(@PathVariable String id) {
        return ReturnResult.ok(deviceSyncService.syncDevicesByCredential(id));
    }

    /**
     * 按凭据同步组织树（拉取方向；落库 device_organization）
     *
     * @param id 凭据 ID
     * @return created / updated / skipped 统计
     */
    @PostMapping("/{id}/sync/org")
    public ReturnResult<Map<String, Integer>> syncOrg(@PathVariable String id) {
        return ReturnResult.ok(deviceSyncService.syncOrganizationsByCredential(id));
    }

    /**
     * 按凭据同步人员列表（拉取方向；落库 device_person）
     *
     * <p>人员与组织表解耦，单建 {@code device_person}（按 vendor + 厂商侧人员标识
     * 幂等入库），避免人员 API 结构换表时耦合到 org-person 关联表。</p>
     *
     * @param id 凭据 ID
     * @return created / updated / skipped 统计
     */
    @PostMapping("/{id}/sync/persons")
    public ReturnResult<Map<String, Integer>> syncPersons(@PathVariable String id) {
        return ReturnResult.ok(deviceSyncService.syncPersonsByCredential(id));
    }

    /**
     * 按凭据同步设备目录 + 组织树 + 人员列表（全量）
     *
     * @param id 凭据 ID
     * @return 各方向的统计
     */
    @PostMapping("/{id}/sync")
    public ReturnResult<Map<String, Map<String, Integer>>> syncAll(@PathVariable String id) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        result.put("devices", deviceSyncService.syncDevicesByCredential(id));
        result.put("organizations", deviceSyncService.syncOrganizationsByCredential(id));
        result.put("persons", deviceSyncService.syncPersonsByCredential(id));
        return ReturnResult.ok(result);
    }
}